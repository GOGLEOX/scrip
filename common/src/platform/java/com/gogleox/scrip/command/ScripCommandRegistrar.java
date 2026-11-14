package com.gogleox.scrip.command;

import com.gogleox.scrip.account.AccountControlOutcome;
import com.gogleox.scrip.account.AdministrativeAuditRecord;
import com.gogleox.scrip.account.AuditActor;
import com.gogleox.scrip.account.TransactionRecord;
import com.gogleox.scrip.account.TransferOutcome;
import com.mojang.logging.LogUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Minecraft command tree shared by the loader adapters; no loader event types leak here. */
public final class ScripCommandRegistrar {
    private static final int ADMIN_PERMISSION = 2;
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private final Supplier<Optional<CommandService>> commands;

    public ScripCommandRegistrar(Supplier<Optional<CommandService>> commands) { this.commands = commands; }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher, String aliasProperty) {
        registerRoot(dispatcher, "scrip");
        List<String> aliases = CommandAliasPolicy.configuredAliases(aliasProperty);
        List<String> accepted = CommandAliasPolicy.withoutConflicts(aliases,
                dispatcher.getRoot().getChildren().stream().map(node -> node.getName()).toList());
        for (String alias : aliases) if (!accepted.contains(alias)) LOGGER.warn("Scrip command alias '{}' was not registered because another command already owns it", alias);
        for (String alias : accepted) registerRoot(dispatcher, alias);
    }

    private void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        if (dispatcher.getRoot().getChild(name) != null) {
            if (name.equals("scrip")) LOGGER.error("Scrip command namespace is already registered; Scrip will not override another mod command");
            return;
        }
        dispatcher.register(Commands.literal(name)
                .then(Commands.literal("balance").executes(this::ownBalance)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION)).executes(this::otherBalance)))
                .then(Commands.literal("pay").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(this::pay))))
                .then(Commands.literal("history").executes(this::history))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("balance").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(this::otherBalance)))
                        .then(adjustment("grant", false)).then(adjustment("deduct", true))
                        .then(control("freeze", true)).then(control("unfreeze", false))
                        .then(Commands.literal("audit")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(this::audit)))));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> adjustment(String name, boolean negative) {
        return Commands.literal(name).then(Commands.argument("player", GameProfileArgument.gameProfile())
                .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(context -> adjust(context, negative, "command"))
                        .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(context -> adjust(context, negative, StringArgumentType.getString(context, "reason"))))));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> control(String name, boolean frozen) {
        return Commands.literal(name).then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(context -> control(context, frozen, "command"))
                .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(context -> control(context, frozen, StringArgumentType.getString(context, "reason")))));
    }

    private int ownBalance(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try { player = context.getSource().getPlayerOrException(); } catch (Exception exception) { return failure(context, "This command requires a player."); }
        return showBalance(context, player.getUUID(), player.getGameProfile().getName());
    }
    private int otherBalance(CommandContext<CommandSourceStack> context) { return target(context).map(target -> showBalance(context, target.id(), target.name())).orElseGet(() -> failure(context, "Unknown player account.")); }
    private int showBalance(CommandContext<CommandSourceStack> context, UUID id, String name) {
        Optional<CommandService> service = commands.get(); if (service.isEmpty()) return failure(context, "Scrip is not ready.");
        return service.get().balanceByVerifiedPlayer(id).map(account -> success(context, name + ": " + account.balance().units() + " " + "Coins" + " (" + account.lifecycle().name().toLowerCase() + ")")).orElseGet(() -> failure(context, "Unknown player account."));
    }
    private int pay(CommandContext<CommandSourceStack> context) {
        ServerPlayer sender;
        try { sender = context.getSource().getPlayerOrException(); } catch (Exception exception) { return failure(context, "This command requires a player."); }
        Optional<Target> target = target(context); if (target.isEmpty()) return failure(context, "Unknown player account.");
        Optional<CommandService> service = commands.get(); if (service.isEmpty()) return failure(context, "Scrip is not ready.");
        long amount = LongArgumentType.getLong(context, "amount");
        TransferOutcome outcome = service.get().pay(sender.getUUID(), target.get().id(), amount, UUID.randomUUID());
        if (outcome.status() == TransferOutcome.Status.SUCCESS) return success(context, "Paid " + target.get().name() + " " + amount + " Coins.");
        return failure(context, transferFailure(outcome));
    }
    private int history(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try { player = context.getSource().getPlayerOrException(); } catch (Exception exception) { return failure(context, "This command requires a player."); }
        Optional<CommandService> service = commands.get(); if (service.isEmpty()) return failure(context, "Scrip is not ready.");
        List<TransactionRecord> entries = service.get().history(player.getUUID());
        if (entries.isEmpty()) return success(context, "No transaction history.");
        for (TransactionRecord entry : entries.stream().limit(10).toList()) success(context, entry.direction().name().toLowerCase() + " " + entry.amount() + " Coins: " + entry.description());
        return 1;
    }
    private int adjust(CommandContext<CommandSourceStack> context, boolean negative, String reason) {
        Optional<Target> target = target(context); if (target.isEmpty()) return failure(context, "Unknown player account.");
        Optional<CommandService> service = commands.get(); if (service.isEmpty()) return failure(context, "Scrip is not ready.");
        long amount = LongArgumentType.getLong(context, "amount");
        TransferOutcome outcome = service.get().adjust(target.get().id(), negative ? -amount : amount, reason, actor(context), UUID.randomUUID());
        if (outcome.status() == TransferOutcome.Status.SUCCESS) return success(context, (negative ? "Deducted " : "Granted ") + amount + " Coins for " + target.get().name() + ".");
        return failure(context, transferFailure(outcome));
    }
    private int control(CommandContext<CommandSourceStack> context, boolean frozen, String reason) {
        Optional<Target> target = target(context); if (target.isEmpty()) return failure(context, "Unknown player account.");
        Optional<CommandService> service = commands.get(); if (service.isEmpty()) return failure(context, "Scrip is not ready.");
        AccountControlOutcome outcome = service.get().setFrozen(target.get().id(), frozen, reason, actor(context), UUID.randomUUID());
        if (outcome.status() == AccountControlOutcome.Status.SUCCESS) return success(context, target.get().name() + (frozen ? " is frozen." : " is unfrozen."));
        return failure(context, outcome.status().name().toLowerCase().replace('_', ' ') + ".");
    }
    private int audit(CommandContext<CommandSourceStack> context) {
        Optional<Target> target = target(context); if (target.isEmpty()) return failure(context, "Unknown player account.");
        Optional<CommandService> service = commands.get(); if (service.isEmpty()) return failure(context, "Scrip is not ready.");
        showBalance(context, target.get().id(), target.get().name());
        List<AdministrativeAuditRecord> records = service.get().audit(target.get().id());
        List<TransactionRecord> transactions = service.get().history(target.get().id());
        if (records.isEmpty() && transactions.isEmpty()) return success(context, "No audit records.");
        for (TransactionRecord entry : transactions.stream().limit(10).toList()) success(context, "transaction " + entry.direction().name().toLowerCase() + ": " + entry.amount() + " Coins; " + entry.description());
        for (AdministrativeAuditRecord record : records.stream().limit(10).toList()) success(context, record.action().name().toLowerCase() + " by " + record.actor().displayName() + ": " + record.amount() + " Coins; " + record.reason());
        return 1;
    }
    private Optional<Target> target(CommandContext<CommandSourceStack> context) {
        try {
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
            if (profiles.size() != 1) return Optional.empty();
            GameProfile profile = profiles.iterator().next();
            if (profile.getId() == null || profile.getName() == null) return Optional.empty();
            return commands.get().flatMap(service -> service.balanceByVerifiedPlayer(profile.getId()).map(account -> new Target(profile.getId(), profile.getName())));
        } catch (Exception exception) { return Optional.empty(); }
    }
    private AuditActor actor(CommandContext<CommandSourceStack> context) {
        try { ServerPlayer player = context.getSource().getPlayerOrException(); return AuditActor.player(player.getUUID(), player.getGameProfile().getName()); }
        catch (Exception ignored) { return AuditActor.console(); }
    }
    private static String transferFailure(TransferOutcome outcome) { return switch (outcome.status()) {
        case INVALID_AMOUNT -> "Amount must be positive.";
        case FROZEN_ACCOUNT -> "The sending account is frozen.";
        case INSUFFICIENT_FUNDS -> "Insufficient funds.";
        case DESTINATION_OVERFLOW -> "Balance limit reached.";
        case UNKNOWN_ACCOUNT -> "Unknown player account.";
        default -> "Transaction failed: " + outcome.status().name().toLowerCase().replace('_', ' ') + ".";
    }; }
    private static int success(CommandContext<CommandSourceStack> context, String message) { context.getSource().sendSuccess(() -> Component.literal(message), false); return 1; }
    private static int failure(CommandContext<CommandSourceStack> context, String message) { context.getSource().sendFailure(Component.literal(message)); return 0; }
    private record Target(UUID id, String name) {}
}
