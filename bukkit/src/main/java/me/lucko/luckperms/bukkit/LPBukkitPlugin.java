/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.bukkit;

import lombok.Getter;

import com.google.common.collect.ImmutableMap;

import me.lucko.luckperms.api.Contexts;
import me.lucko.luckperms.api.Logger;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.PlatformType;
import me.lucko.luckperms.api.context.ContextSet;
import me.lucko.luckperms.api.context.MutableContextSet;
import me.lucko.luckperms.bukkit.inject.Injector;
import me.lucko.luckperms.bukkit.messaging.BungeeMessagingService;
import me.lucko.luckperms.bukkit.messaging.LilyPadMessagingService;
import me.lucko.luckperms.bukkit.model.ChildPermissionProvider;
import me.lucko.luckperms.bukkit.model.DefaultsProvider;
import me.lucko.luckperms.bukkit.model.LPPermissible;
import me.lucko.luckperms.bukkit.vault.VaultHook;
import me.lucko.luckperms.common.LuckPermsPlugin;
import me.lucko.luckperms.common.api.ApiHandler;
import me.lucko.luckperms.common.api.ApiProvider;
import me.lucko.luckperms.common.caching.handlers.CachedStateManager;
import me.lucko.luckperms.common.calculators.CalculatorFactory;
import me.lucko.luckperms.common.commands.sender.Sender;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.config.LPConfiguration;
import me.lucko.luckperms.common.constants.Permission;
import me.lucko.luckperms.common.contexts.ContextManager;
import me.lucko.luckperms.common.contexts.ServerCalculator;
import me.lucko.luckperms.common.core.UuidCache;
import me.lucko.luckperms.common.core.model.User;
import me.lucko.luckperms.common.data.Importer;
import me.lucko.luckperms.common.debug.DebugHandler;
import me.lucko.luckperms.common.dependencies.DependencyManager;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.NoopLocaleManager;
import me.lucko.luckperms.common.locale.SimpleLocaleManager;
import me.lucko.luckperms.common.managers.GroupManager;
import me.lucko.luckperms.common.managers.TrackManager;
import me.lucko.luckperms.common.managers.UserManager;
import me.lucko.luckperms.common.managers.impl.GenericGroupManager;
import me.lucko.luckperms.common.managers.impl.GenericTrackManager;
import me.lucko.luckperms.common.managers.impl.GenericUserManager;
import me.lucko.luckperms.common.messaging.InternalMessagingService;
import me.lucko.luckperms.common.messaging.RedisMessaging;
import me.lucko.luckperms.common.storage.Storage;
import me.lucko.luckperms.common.storage.StorageFactory;
import me.lucko.luckperms.common.storage.StorageType;
import me.lucko.luckperms.common.tasks.CacheHousekeepingTask;
import me.lucko.luckperms.common.tasks.ExpireTemporaryTask;
import me.lucko.luckperms.common.tasks.UpdateTask;
import me.lucko.luckperms.common.utils.BufferedRequest;
import me.lucko.luckperms.common.utils.LoggerImpl;
import me.lucko.luckperms.common.utils.PermissionCache;

import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Getter
public class LPBukkitPlugin extends JavaPlugin implements LuckPermsPlugin {
    private Set<UUID> ignoringLogs;
    private Set<Runnable> shutdownHooks;
    private Executor syncExecutor;
    private Executor asyncExecutor;
    private Executor asyncBukkitExecutor;
    private ExecutorService asyncLpExecutor;
    private VaultHook vaultHook = null;
    private LPConfiguration configuration;
    private UserManager userManager;
    private GroupManager groupManager;
    private TrackManager trackManager;
    private Storage storage;
    private InternalMessagingService messagingService = null;
    private UuidCache uuidCache;
    private BukkitListener listener;
    private ApiProvider apiProvider;
    private Logger log;
    private Importer importer;
    private DefaultsProvider defaultsProvider;
    private ChildPermissionProvider childPermissionProvider;
    private LocaleManager localeManager;
    private CachedStateManager cachedStateManager;
    private ContextManager<Player> contextManager;
    private WorldCalculator worldCalculator;
    private CalculatorFactory calculatorFactory;
    private BufferedRequest<Void> updateTaskBuffer;
    private boolean started = false;
    private DebugHandler debugHandler;
    private BukkitSenderFactory senderFactory;
    private PermissionCache permissionCache;

    @Override
    public void onEnable() {
        // Used whilst the plugin is enabling / disabling / disabled
        asyncLpExecutor = Executors.newCachedThreadPool();
        asyncBukkitExecutor = r -> getServer().getScheduler().runTaskAsynchronously(this, r);

        asyncExecutor = asyncLpExecutor;
        syncExecutor = r -> getServer().getScheduler().runTask(this, r);

        localeManager = new NoopLocaleManager();
        senderFactory = new BukkitSenderFactory(this);
        log = new LoggerImpl(getConsoleSender());
        LuckPermsPlugin.sendStartupBanner(getConsoleSender(), this);

        ignoringLogs = ConcurrentHashMap.newKeySet();
        shutdownHooks = Collections.synchronizedSet(new HashSet<>());
        debugHandler = new DebugHandler(asyncBukkitExecutor, getVersion());
        permissionCache = new PermissionCache(asyncBukkitExecutor);

        getLog().info("Loading configuration...");
        configuration = new BukkitConfig(this);
        configuration.init();
        configuration.loadAll();

        Set<StorageType> storageTypes = StorageFactory.getRequiredTypes(this, StorageType.H2);
        DependencyManager.loadDependencies(this, storageTypes);

        // setup the Bukkit defaults hook
        defaultsProvider = new DefaultsProvider();
        childPermissionProvider = new ChildPermissionProvider();

        // give all plugins a chance to load their defaults, then refresh.
        getServer().getScheduler().runTaskLater(this, () -> {
            defaultsProvider.refresh();
            childPermissionProvider.setup();

            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                for (Map.Entry<String, Boolean> e : defaultsProvider.getOpDefaults().entrySet()) {
                    permissionCache.offer(e.getKey());
                }

                for (Map.Entry<String, Boolean> e : defaultsProvider.getNonOpDefaults().entrySet()) {
                    permissionCache.offer(e.getKey());
                }

                ImmutableMap<Map.Entry<String, Boolean>, ImmutableMap<String, Boolean>> permissions = childPermissionProvider.getPermissions();
                for (Map.Entry<Map.Entry<String, Boolean>, ImmutableMap<String, Boolean>> e : permissions.entrySet()) {
                    permissionCache.offer(e.getKey().getKey());
                    for (Map.Entry<String, Boolean> e1 : e.getValue().entrySet()) {
                        permissionCache.offer(e1.getKey());
                    }
                }
            });

        }, 1L);

        // register events
        PluginManager pm = getServer().getPluginManager();
        listener = new BukkitListener(this);
        pm.registerEvents(listener, this);

        // initialise datastore
        storage = StorageFactory.getInstance(this, StorageType.H2);

        // initialise messaging
        String messagingType = getConfiguration().get(ConfigKeys.MESSAGING_SERVICE).toLowerCase();
        if (messagingType.equals("none") && getConfiguration().get(ConfigKeys.REDIS_ENABLED)) {
            messagingType = "redis";
        }
        if (messagingType.equals("redis")) {
            getLog().info("Loading redis...");
            if (getConfiguration().get(ConfigKeys.REDIS_ENABLED)) {
                RedisMessaging redis = new RedisMessaging(this);
                try {
                    redis.init(getConfiguration().get(ConfigKeys.REDIS_ADDRESS), getConfiguration().get(ConfigKeys.REDIS_PASSWORD));
                    getLog().info("Loaded redis successfully...");

                    messagingService = redis;
                } catch (Exception e) {
                    getLog().warn("Couldn't load redis...");
                    e.printStackTrace();
                }
            } else {
                getLog().warn("Messaging Service was set to redis, but redis is not enabled!");
            }
        } else if (messagingType.equals("bungee")) {
            getLog().info("Loading bungee messaging service...");
            BungeeMessagingService bungeeMessaging = new BungeeMessagingService(this);
            bungeeMessaging.init();
            messagingService = bungeeMessaging;
        } else if (messagingType.equals("lilypad")) {
            getLog().info("Loading LilyPad messaging service...");
            if (getServer().getPluginManager().getPlugin("LilyPad-Connect") == null) {
                getLog().warn("LilyPad-Connect plugin not present.");
            } else {
                LilyPadMessagingService lilyPadMessaging = new LilyPadMessagingService(this);
                lilyPadMessaging.init();
                messagingService = lilyPadMessaging;
            }
        } else if (!messagingType.equals("none")) {
            getLog().warn("Messaging service '" + messagingType + "' not recognised.");
        }

        // setup the update task buffer
        updateTaskBuffer = new BufferedRequest<Void>(1000L, this::doAsync) {
            @Override
            protected Void perform() {
                new UpdateTask(LPBukkitPlugin.this).run();
                return null;
            }
        };

        // load locale
        localeManager = new SimpleLocaleManager();
        File locale = new File(getDataFolder(), "lang.yml");
        if (locale.exists()) {
            getLog().info("Found locale file. Attempting to load from it.");
            try {
                localeManager.loadFromFile(locale);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // register commands
        getLog().info("Registering commands...");
        BukkitCommand commandManager = new BukkitCommand(this);
        PluginCommand main = getServer().getPluginCommand("luckperms");
        main.setExecutor(commandManager);
        main.setTabCompleter(commandManager);
        main.setAliases(Arrays.asList("perms", "lp", "permissions", "perm"));

        // load internal managers
        getLog().info("Loading internal permission managers...");
        uuidCache = new UuidCache(this);
        userManager = new GenericUserManager(this);
        groupManager = new GenericGroupManager(this);
        trackManager = new GenericTrackManager();
        importer = new Importer(commandManager);
        calculatorFactory = new BukkitCalculatorFactory(this);
        cachedStateManager = new CachedStateManager(this);

        contextManager = new ContextManager<>();
        worldCalculator = new WorldCalculator(this);
        pm.registerEvents(worldCalculator, this);
        contextManager.registerCalculator(worldCalculator);
        contextManager.registerCalculator(new ServerCalculator<>(getConfiguration()));

        // Provide vault support
        tryVaultHook(false);

        // register with the LP API
        getLog().info("Registering API...");
        apiProvider = new ApiProvider(this);
        ApiHandler.registerProvider(apiProvider);
        getServer().getServicesManager().register(LuckPermsApi.class, apiProvider, this, ServicePriority.Normal);


        // schedule update tasks
        int mins = getConfiguration().get(ConfigKeys.SYNC_TIME);
        if (mins > 0) {
            long ticks = mins * 60 * 20;
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> updateTaskBuffer.request(), 40L, ticks);
        }

        // run an update instantly.
        updateTaskBuffer.requestDirectly();

        // register tasks
        getServer().getScheduler().runTaskTimerAsynchronously(this, new ExpireTemporaryTask(this), 60L, 60L);
        getServer().getScheduler().runTaskTimerAsynchronously(this, new CacheHousekeepingTask(this), 2400L, 2400L);

        // register permissions
        registerPermissions(getConfiguration().get(ConfigKeys.COMMANDS_ALLOW_OP) ? PermissionDefault.OP : PermissionDefault.FALSE);
        if (!getConfiguration().get(ConfigKeys.OPS_ENABLED)) {
            getServer().getScheduler().runTask(this, () -> getServer().getOperators().forEach(o -> o.setOp(false)));
        }

        // replace the temporary executor when the Bukkit one starts
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            asyncExecutor = asyncBukkitExecutor;
        });

        // Load any online users (in the case of a reload)
        for (Player player : getServer().getOnlinePlayers()) {
            doAsync(() -> {
                listener.onAsyncLogin(player.getUniqueId(), player.getName());
                User user = getUserManager().get(getUuidCache().getUUID(player.getUniqueId()));
                if (user != null) {
                    doSync(() -> {
                        try {
                            LPPermissible lpPermissible = new LPPermissible(player, user, this);
                            Injector.inject(player, lpPermissible);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    });
                }
            });
        }

        started = true;
        getLog().info("Successfully loaded.");
    }

    @Override
    public void onDisable() {
        // Switch back to the LP executor, the bukkit one won't allow new tasks
        asyncExecutor = asyncLpExecutor;

        started = false;

        shutdownHooks.forEach(Runnable::run);

        defaultsProvider.close();
        permissionCache.setShutdown(true);
        debugHandler.setShutdown(true);

        for (Player player : getServer().getOnlinePlayers()) {
            Injector.unInject(player, false, false);
            if (getConfiguration().get(ConfigKeys.AUTO_OP)) {
                player.setOp(false);
            }

            final User user = getUserManager().get(getUuidCache().getUUID(player.getUniqueId()));
            if (user != null) {
                user.unregisterData();
                getUserManager().unload(user);
            }
        }

        getLog().info("Closing datastore...");
        storage.shutdown();

        if (messagingService != null) {
            getLog().info("Closing messaging service...");
            messagingService.close();
        }

        getLog().info("Unregistering API...");
        ApiHandler.unregisterProvider();
        getServer().getServicesManager().unregisterAll(this);

        if (vaultHook != null) {
            vaultHook.unhook(this);
        }

        // wait for executor
        asyncLpExecutor.shutdown();
        try {
            asyncLpExecutor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Bukkit will do this again when #onDisable completes, but we do it early to prevent NPEs elsewhere.
        getServer().getScheduler().cancelTasks(this);
        HandlerList.unregisterAll(this);

        // Null everything
        ignoringLogs = null;
        vaultHook = null;
        configuration = null;
        userManager = null;
        groupManager = null;
        trackManager = null;
        storage = null;
        messagingService = null;
        uuidCache = null;
        listener = null;
        apiProvider = null;
        log = null;
        importer = null;
        defaultsProvider = null;
        childPermissionProvider = null;
        localeManager = null;
        cachedStateManager = null;
        contextManager = null;
        worldCalculator = null;
        calculatorFactory = null;
        updateTaskBuffer = null;
        debugHandler = null;
        senderFactory = null;
        permissionCache = null;
    }

    public void tryVaultHook(boolean force) {
        if (vaultHook != null) {
            return;
        }

        getLog().info("Attempting to hook with Vault...");
        try {
            if (force || getServer().getPluginManager().isPluginEnabled("Vault")) {
                vaultHook = new VaultHook();
                vaultHook.hook(this);
                getLog().info("Registered Vault permission & chat hook.");
            } else {
                getLog().info("Vault not found.");
            }
        } catch (Exception e) {
            vaultHook = null;
            getLog().severe("Error occurred whilst hooking into Vault.");
            e.printStackTrace();
        }
    }

    @Override
    public void onUserRefresh(User user) {
        LPPermissible lpp = Injector.getPermissible(uuidCache.getExternalUUID(user.getUuid()));
        if (lpp != null) {
            lpp.updateSubscriptions();
        }
    }

    public void refreshAutoOp(Player player) {
        if (getConfiguration().get(ConfigKeys.AUTO_OP)) {
            try {
                LPPermissible permissible = Injector.getPermissible(player.getUniqueId());
                if (permissible == null) {
                    return;
                }

                Map<String, Boolean> backing = permissible.getUser().getUserData().getPermissionData(permissible.calculateContexts()).getImmutableBacking();
                boolean op = Optional.ofNullable(backing.get("luckperms.autoop")).orElse(false);
                player.setOp(op);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void doAsync(Runnable r) {
        asyncExecutor.execute(r);
    }

    @Override
    public void doSync(Runnable r) {
        syncExecutor.execute(r);
    }

    @Override
    public void doAsyncRepeating(Runnable r, long interval) {
        getServer().getScheduler().runTaskTimerAsynchronously(this, r, interval, interval);
    }

    @Override
    public String getVersion() {
        return getDescription().getVersion();
    }

    @Override
    public PlatformType getType() {
        return PlatformType.BUKKIT;
    }

    @Override
    public String getServerName() {
        return getServer().getName();
    }

    @Override
    public String getServerVersion() {
        return getServer().getVersion();
    }

    @Override
    public File getMainDir() {
        return getDataFolder();
    }

    @Override
    public InputStream getResourceStream(String path) {
        return getResource(path);
    }

    @Override
    public Player getPlayer(User user) {
        return getServer().getPlayer(uuidCache.getExternalUUID(user.getUuid()));
    }

    @Override
    public Contexts getContextForUser(User user) {
        Player player = getPlayer(user);
        if (player == null) {
            return null;
        }
        return new Contexts(
                getContextManager().getApplicableContext(player),
                getConfiguration().get(ConfigKeys.INCLUDING_GLOBAL_PERMS),
                getConfiguration().get(ConfigKeys.INCLUDING_GLOBAL_WORLD_PERMS),
                true,
                getConfiguration().get(ConfigKeys.APPLYING_GLOBAL_GROUPS),
                getConfiguration().get(ConfigKeys.APPLYING_GLOBAL_WORLD_GROUPS),
                player.isOp()
        );
    }

    @Override
    public int getPlayerCount() {
        return getServer().getOnlinePlayers().size();
    }

    @Override
    public List<String> getPlayerList() {
        return getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    @Override
    public Set<UUID> getOnlinePlayers() {
        return getServer().getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toSet());
    }

    @Override
    public boolean isOnline(UUID external) {
        return getServer().getPlayer(external) != null;
    }

    @Override
    public List<Sender> getSenders() {
        return getServer().getOnlinePlayers().stream()
                .map(p -> getSenderFactory().wrap(p))
                .collect(Collectors.toList());
    }

    @Override
    public Sender getConsoleSender() {
        return getSenderFactory().wrap(getServer().getConsoleSender());
    }

    @Override
    public Set<Contexts> getPreProcessContexts(boolean op) {
        Set<ContextSet> c = new HashSet<>();
        c.add(ContextSet.empty());
        c.add(ContextSet.singleton("server", getConfiguration().get(ConfigKeys.SERVER)));

        // Pre process all worlds
        c.addAll(getServer().getWorlds().stream()
                .map(World::getName)
                .map(s -> {
                    MutableContextSet set = MutableContextSet.create();
                    set.add("server", getConfiguration().get(ConfigKeys.SERVER));
                    set.add("world", s);
                    return set.makeImmutable();
                })
                .collect(Collectors.toList())
        );

        // Pre process the separate Vault server, if any
        if (!getConfiguration().get(ConfigKeys.SERVER).equals(getConfiguration().get(ConfigKeys.VAULT_SERVER))) {
            c.add(ContextSet.singleton("server", getConfiguration().get(ConfigKeys.VAULT_SERVER)));
            c.addAll(getServer().getWorlds().stream()
                    .map(World::getName)
                    .map(s -> {
                        MutableContextSet set = MutableContextSet.create();
                        set.add("server", getConfiguration().get(ConfigKeys.VAULT_SERVER));
                        set.add("world", s);
                        return set.makeImmutable();
                    })
                    .collect(Collectors.toList())
            );
        }

        Set<Contexts> contexts = new HashSet<>();

        // Convert to full Contexts
        contexts.addAll(c.stream()
                .map(set -> new Contexts(
                        set,
                        getConfiguration().get(ConfigKeys.INCLUDING_GLOBAL_PERMS),
                        getConfiguration().get(ConfigKeys.INCLUDING_GLOBAL_WORLD_PERMS),
                        true,
                        getConfiguration().get(ConfigKeys.APPLYING_GLOBAL_GROUPS),
                        getConfiguration().get(ConfigKeys.APPLYING_GLOBAL_WORLD_GROUPS),
                        op
                ))
                .collect(Collectors.toSet())
        );

        // Check for and include varying Vault config options
        boolean vaultDiff = getConfiguration().get(ConfigKeys.VAULT_INCLUDING_GLOBAL) != getConfiguration().get(ConfigKeys.INCLUDING_GLOBAL_PERMS) ||
                !getConfiguration().get(ConfigKeys.INCLUDING_GLOBAL_WORLD_PERMS) ||
                !getConfiguration().get(ConfigKeys.APPLYING_GLOBAL_GROUPS) ||
                !getConfiguration().get(ConfigKeys.APPLYING_GLOBAL_WORLD_GROUPS);

        if (vaultDiff) {
            contexts.addAll(c.stream()
                    .map(map -> new Contexts(map, getConfiguration().get(ConfigKeys.VAULT_INCLUDING_GLOBAL), true, true, true, true, op))
                    .collect(Collectors.toSet())
            );
        }

        return contexts;
    }

    @Override
    public LinkedHashMap<String, Object> getExtraInfo() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("Vault Enabled", vaultHook != null);
        map.put("Vault Server", configuration.get(ConfigKeys.VAULT_SERVER));
        map.put("Bukkit Defaults count", defaultsProvider.size());
        map.put("Bukkit Child Permissions count", childPermissionProvider.getPermissions().size());
        map.put("Vault Including Global", configuration.get(ConfigKeys.VAULT_INCLUDING_GLOBAL));
        map.put("Vault Ignoring World", configuration.get(ConfigKeys.VAULT_IGNORE_WORLD));
        map.put("Vault Primary Group Overrides", configuration.get(ConfigKeys.VAULT_PRIMARY_GROUP_OVERRIDES));
        map.put("Vault Debug", configuration.get(ConfigKeys.VAULT_DEBUG));
        map.put("OPs Enabled", configuration.get(ConfigKeys.OPS_ENABLED));
        map.put("Auto OP", configuration.get(ConfigKeys.AUTO_OP));
        map.put("Commands Allow OPs", configuration.get(ConfigKeys.COMMANDS_ALLOW_OP));
        return map;
    }

    @Override
    public Object getPlugin(String name) {
        return getServer().getPluginManager().getPlugin(name);
    }

    @Override
    public Object getService(Class clazz) {
        return getServer().getServicesManager().load(clazz);
    }

    @SuppressWarnings("deprecation")
    @Override
    public UUID getUUID(String playerName) {
        try {
            return getServer().getOfflinePlayer(playerName).getUniqueId();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isPluginLoaded(String name) {
        return getServer().getPluginManager().isPluginEnabled(name);
    }

    @Override
    public void addShutdownHook(Runnable r) {
        shutdownHooks.add(r);
    }

    private void registerPermissions(PermissionDefault def) {
        PluginManager pm = getServer().getPluginManager();

        for (Permission p : Permission.values()) {
            for (String node : p.getNodes()) {
                pm.addPermission(new org.bukkit.permissions.Permission(node, def));
            }
        }
    }
}
