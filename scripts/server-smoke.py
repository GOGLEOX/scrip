"""Bounded dedicated-server smoke test. Requires explicit test-server EULA consent."""
import argparse
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import threading
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("loader", choices=("forge", "neoforge"))
    parser.add_argument("--accept-eula", action="store_true")
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--probe", action="store_true", help="Run test-only vanilla protocol client twice")
    parser.add_argument("--production", action="store_true", help="Use an installed clean server under .tools")
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error("Explicit --accept-eula required; review https://aka.ms/MinecraftEULA")
    if not 30 <= args.timeout <= 1800:
        parser.error("timeout must be between 30 and 1800 seconds")
    root = Path(__file__).resolve().parents[1]
    run = root / ".tools" / f"production-{args.loader}" if args.production else root / args.loader / "run"
    run.mkdir(parents=True, exist_ok=True)
    if args.production:
        properties = dict(line.split("=", 1) for line in
                          (root / "gradle.properties").read_text().splitlines()
                          if "=" in line and not line.startswith("#"))
        loader_version = (properties["minecraft_version"] + "-" + properties["forge_version"]
                          if args.loader == "forge" else properties["neo_version"])
        group = "net/minecraftforge" if args.loader == "forge" else "net/neoforged"
        arg_file = Path("libraries") / group / args.loader / loader_version / (
            "win_args.txt" if os.name == "nt" else "unix_args.txt")
        if not (run / arg_file).is_file():
            parser.error("Install the pinned official server into .tools/production-<loader> first")
        jar_name = f"scrip-{properties['minecraft_version']}-{args.loader}-v{properties['mod_version']}.jar"
        mods = run / "mods"
        mods.mkdir(exist_ok=True)
        if any(p.name != jar_name for p in mods.glob("*.jar")):
            parser.error("Production smoke fixture must contain only the current Scrip jar")
        shutil.copyfile(root / args.loader / "build" / "libs" / jar_name, mods / jar_name)
    (run / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    # Local isolated fixture; never expose the offline test server externally.
    (run / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\n"
        "level-name=p00-smoke\nlevel-seed=0\nview-distance=2\nsimulation-distance=2\n"
        "max-players=2\nspawn-protection=0\nmotd=Scrip P00 smoke fixture\n",
        encoding="utf-8",
    )
    if args.production:
        command = ["java", "-Xmx2G", "@" + str(arg_file), "nogui"]
    else:
        command = [str(root / "gradlew.bat")] if os.name == "nt" else ["bash", str(root / "gradlew")]
        command += [f":{args.loader}:runServer", "--console=plain"]
        if args.loader == "forge":
            # ForgeGradle attaches client assets to all runs; a dedicated
            # server does not consume textures or sounds.
            command += ["-x", ":forge:downloadAssets"]
    proc = subprocess.Popen(command, cwd=run if args.production else root, stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8", errors="replace")
    lines = queue.Queue(maxsize=1024)

    def collect():
        for line in proc.stdout:
            lines.put(line)
        lines.put(None)

    threading.Thread(target=collect, daemon=True).start()
    deadline = time.monotonic() + args.timeout
    ready = False
    stopped = False
    probe_ok = not args.probe
    minecraft_ready = False
    mode = "production" if args.production else "development"
    report = root / "build" / "verification" / f"{args.loader}-{mode}-server.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    # Persist only allowlisted evidence, never raw loader logs or machine paths.
    evidence = []
    try:
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                continue
            if line is None:
                break
            if "Scrip P00 server ready" in line:
                ready = True
                evidence.append(f"{args.loader}: Scrip server-started hook observed")
            if 'Done (' in line:
                minecraft_ready = True
            if ready and minecraft_ready and not stopped:
                evidence.append(f"{args.loader}: Minecraft Done marker observed")
                if args.probe:
                    probe = subprocess.run(["node", "scripts/probe/vanilla.cjs"], cwd=root,
                                           capture_output=True, timeout=75, text=True)
                    probe_ok = probe.returncode == 0
                    evidence.append("Vanilla protocol reconnect probe: " + ("PASS" if probe_ok else "FAIL"))
                proc.stdin.write("stop\n")
                proc.stdin.flush()
                stopped = True
            if re.search(r"BUILD (SUCCESSFUL|FAILED)", line):
                evidence.append(re.search(r"BUILD (SUCCESSFUL|FAILED)", line).group())
        if proc.poll() is None:
            proc.wait(timeout=30)
        passed = ready and stopped and probe_ok and proc.returncode == 0
    except subprocess.TimeoutExpired:
        passed = False
    finally:
        if proc.poll() is None:
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            else:
                proc.kill()
    evidence.append("PASS" if passed else "FAIL: startup, shutdown or timeout gate not met")
    report.write_text("\n".join(evidence) + "\n", encoding="utf-8")
    print("\n".join(evidence))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
