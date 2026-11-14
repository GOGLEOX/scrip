package com.gogleox.scrip.command;

import com.gogleox.scrip.account.AccountControlOutcome;
import com.gogleox.scrip.account.AdministrativeAuditRecord;
import com.gogleox.scrip.account.AuditActor;
import com.gogleox.scrip.account.TransactionRecord;
import com.gogleox.scrip.account.TransferOutcome;
import com.gogleox.scrip.maintenance.JournalMaintenancePreview;
import com.gogleox.scrip.maintenance.MaintenanceConfiguration;
import com.gogleox.scrip.maintenance.MaintenanceExclusion;
import com.gogleox.scrip.maintenance.MaintenancePreview;
import com.gogleox.scrip.payment.PaymentPreview;
import com.gogleox.scrip.payment.PaymentResult;
import com.mojang.logging.LogUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
        var admin = Commands.literal("admin").requires(source -> source.hasPermission(ADMIN_PERMISSION))
                .then(Commands.literal("balance").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(this::otherBalance)))
                .then(adjustment("grant", false)).then(adjustment("deduct", true))
                .then(control("freeze", true)).then(control("unfreeze", false))
                .then(Commands.literal("audit")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(this::audit)))
                .then(cleanupMaintenance())
                .then(journalMaintenance());
        dispatcher.register(Commands.literal(name)
                .then(Commands.literal("balance").executes(this::ownBalance)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION)).executes(this::otherBalance)))
                .then(Commands.literal("pay").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(this::pay))))
                .then(Commands.literal("confirm").then(Commands.argument("nonce", StringArgumentType.word()).executes(this::confirmPayment)))
                .then(Commands.literal("history").executes(this::history))
                .then(integration().requires(source -> source.hasPermission(ADMIN_PERMISSION)))
                .then(admin));
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

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> cleanupMaintenance() {
        return Commands.literal("cleanup")
                .then(Commands.literal("preview").executes(context -> cleanupPreview(context, configuredDays(context)))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1)).executes(context -> cleanupPreview(context, IntegerArgumentType.getInteger(context,"days")))))
                .then(Commands.literal("status").executes(this::cleanupStatus))
                .then(Commands.literal("set")
                        .then(Commands.literal("enabled").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(this::setCleanupEnabled)))
                        .then(Commands.literal("inactivity-days").then(Commands.argument("days", IntegerArgumentType.integer(1)).executes(this::setCleanupDays)))
                        .then(Commands.literal("interval-hours").then(Commands.argument("hours", IntegerArgumentType.integer(1)).executes(this::setCleanupInterval))))
                .then(Commands.literal("run").executes(this::notImplemented));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> journalMaintenance() {
        return Commands.literal("journal")
                .then(Commands.literal("compact")
                        .then(Commands.literal("preview").executes(this::journalPreview))
                        .then(Commands.literal("run").executes(this::notImplemented)))
                .then(Commands.literal("verify").executes(this::journalVerify));
    }
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> integration() {
        return Commands.literal("integration").then(Commands.literal("reward")
                .then(Commands.argument("source",StringArgumentType.word())
                        .then(Commands.argument("claim-id",StringArgumentType.word())
                                .then(Commands.argument("player",GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount",LongArgumentType.longArg(1)).executes(this::integrationReward))))));
    }
    private int integrationReward(CommandContext<CommandSourceStack> context) {
        Optional<Target> target=target(context);if(target.isEmpty())return failure(context,"Unknown player account.");Optional<CommandService> service=commands.get();if(service.isEmpty())return failure(context,"Scrip is not ready.");
        try { String source=StringArgumentType.getString(context,"source"),claim=StringArgumentType.getString(context,"claim-id");long amount=LongArgumentType.getLong(context,"amount");com.gogleox.scrip.api.IntegrationRewardResponse result;if("serilum-starter-kit".equals(source)){ServerPlayer commandPlayer=context.getSource().getEntity() instanceof ServerPlayer player?player:null;if(commandPlayer==null)return failure(context,"Starter Kit reward requires its verified player command context.");result=service.get().starterKitGrant(commandPlayer.getUUID(),target.get().id(),claim,amount);}else if("ftb-quests".equals(source)){ServerPlayer commandPlayer=context.getSource().getEntity() instanceof ServerPlayer player?player:null;if(commandPlayer==null)return failure(context,"FTB Quests reward requires its verified player command context.");result=service.get().ftbQuestReward(commandPlayer.getUUID(),target.get().id(),claim,amount);}else result=service.get().integrationReward(source,claim,target.get().id(),amount); return switch(result.status()){case ISSUED->success(context,"Integration reward issued.");case DUPLICATE->success(context,"Integration reward was already issued.");case CONFLICT->failure(context,"Claim identifier conflicts with an existing reward.");case LIMIT_EXCEEDED->failure(context,"Reward amount exceeds the source limit.");case UNAUTHORIZED_SOURCE->failure(context,"Integration reward source is not authorized.");case UNKNOWN_PLAYER->failure(context,"Unknown player account.");default->failure(context,"Integration reward rejected: "+result.status().name().toLowerCase()+".");}; }
        catch(IllegalArgumentException exception){return failure(context,"Invalid integration reward request.");}
    }

    private int cleanupStatus(CommandContext<CommandSourceStack> context) {
        Optional<CommandService> service=commands.get(); if(service.isEmpty()) return failure(context,"Scrip is not ready.");
        MaintenanceConfiguration configuration=service.get().maintenanceConfiguration();
        return success(context,"Cleanup: enabled="+configuration.enabled()+", inactivity-days="+configuration.inactivityDays()+", interval-hours="+configuration.intervalHours()+", batch="+configuration.maximumBatch()+".");
    }
    private int cleanupPreview(CommandContext<CommandSourceStack> context,int days) {
        Optional<CommandService> service=commands.get(); if(service.isEmpty()) return failure(context,"Scrip is not ready.");
        try {
            MaintenancePreview preview=service.get().previewMaintenance(days,onlinePlayers(context));
            success(context,"Cleanup preview: scanned="+preview.scannedAccounts()+", unscanned="+preview.unscannedAccounts()+", eligible="+preview.eligibleAccounts()+".");
            for(MaintenanceExclusion exclusion:MaintenanceExclusion.values()) success(context,exclusion.name().toLowerCase().replace('_',' ')+"="+preview.excludedByReason().get(exclusion));
            return 1;
        } catch(IllegalArgumentException exception) { return failure(context,"Invalid inactivity-days value."); }
    }
    private int setCleanupEnabled(CommandContext<CommandSourceStack> context) {
        Optional<CommandService> service=commands.get(); if(service.isEmpty()) return failure(context,"Scrip is not ready.");
        MaintenanceConfiguration configuration=service.get().maintenanceConfiguration().withEnabled(BoolArgumentType.getBool(context,"enabled"));
        return saveMaintenanceConfiguration(context,service.get(),configuration);
    }
    private int setCleanupDays(CommandContext<CommandSourceStack> context) {
        Optional<CommandService> service=commands.get(); if(service.isEmpty()) return failure(context,"Scrip is not ready.");
        try { return saveMaintenanceConfiguration(context,service.get(),service.get().maintenanceConfiguration().withInactivityDays(IntegerArgumentType.getInteger(context,"days"))); }
        catch(IllegalArgumentException exception) { return failure(context,"inactivity-days must be between 30 and 3650."); }
    }
    private int setCleanupInterval(CommandContext<CommandSourceStack> context) {
        Optional<CommandService> service=commands.get(); if(service.isEmpty()) return failure(context,"Scrip is not ready.");
        try { return saveMaintenanceConfiguration(context,service.get(),service.get().maintenanceConfiguration().withIntervalHours(IntegerArgumentType.getInteger(context,"hours"))); }
        catch(IllegalArgumentException exception) { return failure(context,"interval-hours must be between 1 and 168."); }
    }
    private int saveMaintenanceConfiguration(CommandContext<CommandSourceStack> context,CommandService service,MaintenanceConfiguration configuration) {
        try { service.updateMaintenanceConfiguration(UUID.randomUUID(),configuration); return success(context,"Cleanup configuration saved."); }
        catch(RuntimeException exception) { return failure(context,"Cleanup configuration could not be saved."); }
    }
    private int journalPreview(CommandContext<CommandSourceStack> context) {
        Optional<CommandService> service=commands.get(); if(service.isEmpty()) return failure(context,"Scrip is not ready.");
        try { JournalMaintenancePreview preview=service.get().journalMaintenancePreview(); return success(context,"Journal compact preview: sequence="+preview.latestSequence()+", replay-entries="+preview.replayEntryCount()+", checkpoint="+preview.hasCheckpoint()+". No records changed."); }
        catch(RuntimeException exception) { return failure(context,"Journal inspection failed."); }
    }
    private int journalVerify(CommandContext<CommandSourceStack> context) {
        Optional<CommandService> service=commands.get(); if(service.isEmpty()) return failure(context,"Scrip is not ready.");
        try { JournalMaintenancePreview preview=service.get().journalMaintenancePreview(); return success(context,"Journal verified: sequence="+preview.latestSequence()+", replay-entries="+preview.replayEntryCount()+", checkpoint="+preview.hasCheckpoint()+"."); }
        catch(RuntimeException exception) { return failure(context,"Journal verification failed."); }
    }
    private int notImplemented(CommandContext<CommandSourceStack> context) { return failure(context,"NOT_IMPLEMENTED: available in P10-M."); }
    private int configuredDays(CommandContext<CommandSourceStack> context) { return commands.get().map(service -> service.maintenanceConfiguration().inactivityDays()).orElse(MaintenanceConfiguration.defaults().inactivityDays()); }
    private static Set<UUID> onlinePlayers(CommandContext<CommandSourceStack> context) { return context.getSource().getServer().getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toUnmodifiableSet()); }

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
        long amount = LongArgumentType.getLong(context, "amount"); PaymentResult result=service.get().previewPersonalPayment(sender.getUUID(),target.get().id(),amount);
        if(result.status()!=PaymentResult.Status.PREVIEW_READY)return failure(context,"Payment preview failed: "+result.status().name().toLowerCase()+"."); PaymentPreview preview=result.preview().orElseThrow();
        if(preview.confirmationRequired())return success(context,"Payment preview: amount="+amount+", fee="+preview.fee()+", total="+preview.totalDebit()+". Confirm with /scrip confirm "+preview.nonce());
        PaymentResult executed=service.get().executePreview(sender.getUUID(),preview);return executed.status()==PaymentResult.Status.EXECUTED?success(context,"Paid "+target.get().name()+" "+amount+" Coins; fee="+preview.fee()+"."):failure(context,"Payment failed: "+executed.status().name().toLowerCase()+".");
    }
    private int confirmPayment(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;try{player=context.getSource().getPlayerOrException();}catch(Exception exception){return failure(context,"This command requires a player.");} Optional<CommandService> service=commands.get();if(service.isEmpty())return failure(context,"Scrip is not ready.");try{PaymentResult result=service.get().confirmPayment(player.getUUID(),UUID.fromString(StringArgumentType.getString(context,"nonce")));return result.status()==PaymentResult.Status.EXECUTED?success(context,"Payment confirmed."):failure(context,"Confirmation failed: "+result.status().name().toLowerCase()+".");}catch(IllegalArgumentException exception){return failure(context,"Invalid confirmation nonce.");}
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
