package pers.xanadu.enderdragon.util;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import pers.xanadu.enderdragon.EnderDragon;
import pers.xanadu.enderdragon.config.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class NMSUtil {

    private Method getNMSWorld;
    private Method asNMSCopy;
    private Method asBukkitCopy;

    /**
     * Поверхностная копия unhandledTags.
     */
    private Method asCraftCopy;

    @Deprecated
    private Method NMSItemStack_save;

    @Deprecated
    private Method cpdToEi;

    private Method stringToCPD;
    private Method PDCtoCPD;

    @Deprecated
    private Method PersistentDataContainer_putAll;

    @Deprecated
    private Method NBTTagCompound_set;

    private Field unhandledTags;
    private Field world_c_environment;
    private Field NBTTagCompound_map;

    private Class<?> WorldClass;
    private Class<?> CraftWorldClass;
    private Class<?> WorldProviderClass;
    private Class<?> CraftItemStackClass;

    @Getter
    private Class<?> CraftMetaItemClass;

    @Getter
    private Class<?> NMSItemStackClass;

    private Class<?> CraftEntityClass;
    private Class<?> NBTTagCompoundClass;
    private Class<?> NBTBaseClass;

    @Getter
    private Class<?> MojangsonParserClass;

    private Class<?> CraftPersistentDataContainerClass;

    public void init() {
        String version = Version.getVersion();

        try {
            this.CraftWorldClass = ClassResolver.obc("CraftWorld");

            this.getNMSWorld = CraftWorldClass.getDeclaredMethod("getHandle");
            this.getNMSWorld.setAccessible(true);

            this.WorldClass = this.getNMSWorld.getReturnType().getSuperclass();

            this.world_c_environment =
                    CraftWorldClass.getDeclaredField("environment");
            this.world_c_environment.setAccessible(true);

            /*
             * Здесь должна находиться остальная инициализация NMS-классов
             * и методов из исходного проекта.
             */

            if (Version.mcMainVersion >= 12
                    && Version.mcMainVersion <= 13
                    && NMSItemStackClass != null) {

                try {
                    this.cpdToEi = NMSItemStackClass.getMethod(
                            "a",
                            NBTTagCompoundClass
                    );
                } catch (NoSuchMethodException exception) {
                    for (Method method : NMSItemStackClass.getMethods()) {
                        if (method.getParameterCount() == 1
                                && method.getParameterTypes()[0]
                                .isAssignableFrom(NBTTagCompoundClass)
                                && method.getReturnType() == NMSItemStackClass) {

                            this.cpdToEi = method;
                            break;
                        }
                    }
                }

                if (this.cpdToEi != null) {
                    this.cpdToEi.setAccessible(true);
                }
            }

            if (Version.mcMainVersion <= 16) {
                this.MojangsonParserClass = Class.forName(
                        "net.minecraft.server."
                                + version
                                + ".MojangsonParser"
                );
            } else if (Version.mcMainVersion < 26) {
                this.MojangsonParserClass = Class.forName(
                        "net.minecraft.nbt.MojangsonParser"
                );
            } else {
                try {
                    this.MojangsonParserClass = Class.forName(
                            "net.minecraft.nbt.MojangsonParser"
                    );
                } catch (ClassNotFoundException exception) {
                    try {
                        this.MojangsonParserClass = Class.forName(
                                "net.minecraft.nbt.CompoundTag"
                        );
                    } catch (ClassNotFoundException ignored) {
                        this.MojangsonParserClass = null;
                    }
                }
            }

            if (this.MojangsonParserClass != null) {
                this.stringToCPD = MojangsonParserClass.getMethod(
                        getMethodName(ReflectiveMethod.MojangsonParser_parse),
                        String.class
                );
                this.stringToCPD.setAccessible(true);
            }

            if (Version.mcMainVersion >= 14) {
                this.CraftPersistentDataContainerClass =
                        ClassResolver.obc(
                                "persistence.CraftPersistentDataContainer"
                        );

                this.PDCtoCPD =
                        CraftPersistentDataContainerClass
                                .getDeclaredMethod("toTagCompound");

                this.PDCtoCPD.setAccessible(true);

                if (!Version.isNBT_UPDATE()) {
                    this.PersistentDataContainer_putAll =
                            CraftPersistentDataContainerClass
                                    .getDeclaredMethod(
                                            "putAll",
                                            NBTTagCompoundClass
                                    );

                    this.PersistentDataContainer_putAll.setAccessible(true);
                }
            }

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }

        if (Config.debug) {
            check();
        }
    }

    public static final class ClassResolver {

        private static final String OBC_BASE = "org.bukkit.craftbukkit";
        private static final String NMS_LEGACY_BASE = "net.minecraft.server";
        private static final String NMS_MODERN_BASE = "net.minecraft";

        private static boolean isModern() {
            return Version.mcMainVersion >= 26;
        }

        public static Class<?> obc(String path)
                throws ClassNotFoundException {

            if (isModern()) {
                return Class.forName(OBC_BASE + "." + path);
            }

            return Class.forName(
                    OBC_BASE + "." + Version.getVersion() + "." + path
            );
        }

        public static Class<?> nms(String path)
                throws ClassNotFoundException {

            if (isModern()) {
                return Class.forName(NMS_MODERN_BASE + "." + path);
            }

            return Class.forName(
                    NMS_LEGACY_BASE + "." + Version.getVersion() + "." + path
            );
        }
    }

    public void check() {
        try {
            Field[] fields = NMSUtil.class.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                Bukkit.getLogger().info(
                        field.getName() + " -> " + field.get(this)
                );
            }
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    @Deprecated
    public ItemStack mergeItemCPD(
            final ItemStack item,
            final Object cpd
    ) {
        Map<String, Object> self = cpdToMap(getCPD(item));
        Map<String, Object> rhs = cpdToMap(cpd);

        self.putAll(rhs);

        Object newCpd = getNBTTagCompound(self);

        return EnderDragon.getInstance()
                .getNMSItemManager()
                .cpdToItem(newCpd);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> cpdToMap(final Object cpd) {
        try {
            return (Map<String, Object>) NBTTagCompound_map.get(cpd);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    public Object getNBTTagCompound(final Map<String, Object> map) {
        try {
            Object cpd = NBTTagCompoundClass
                    .getDeclaredConstructor()
                    .newInstance();

            map.forEach((key, value) -> {
                Object result = value;

                if (value instanceof Map<?, ?>) {
                    result = getNBTTagCompound(
                            (Map<String, Object>) value
                    );
                }

                try {
                    NBTTagCompound_set.invoke(cpd, key, result);
                } catch (ReflectiveOperationException exception) {
                    throw new RuntimeException(
                            "Ошибка reflection при записи NBT",
                            exception
                    );
                }
            });

            return cpd;

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @Deprecated
    public void setPersistentDataContainer(
            final PersistentDataContainer dataContainer,
            final Object cpd
    ) {
        try {
            Object pdc = CraftPersistentDataContainerClass.cast(
                    dataContainer
            );

            PersistentDataContainer_putAll.invoke(pdc, cpd);

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public String PDCtoString(
            final PersistentDataContainer dataContainer
    ) {
        try {
            Object pdc = CraftPersistentDataContainerClass.cast(
                    dataContainer
            );

            return PDCtoCPD.invoke(pdc).toString();

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @Deprecated
    public String getNBT(final ItemStack item) {
        try {
            Object craftItem = CraftItemStackClass.isInstance(item)
                    ? CraftItemStackClass.cast(item)
                    : asCraftCopy.invoke(null, item);

            Object nmsItem = asNMSCopy.invoke(null, craftItem);

            Object cpd = NMSItemStack_save.invoke(
                    nmsItem,
                    NBTTagCompoundClass
                            .getDeclaredConstructor()
                            .newInstance()
            );

            return cpd.toString();

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return "{id:\"minecraft:air\"}";
        }
    }

    @Deprecated
    public Object getCPD(final ItemStack item) {
        try {
            Object craftItem = CraftItemStackClass.isInstance(item)
                    ? CraftItemStackClass.cast(item)
                    : asCraftCopy.invoke(null, item);

            Object nmsItem = asNMSCopy.invoke(null, craftItem);

            return NMSItemStack_save.invoke(
                    nmsItem,
                    NBTTagCompoundClass
                            .getDeclaredConstructor()
                            .newInstance()
            );

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public Object getCPD(final String nbt) {
        try {
            return stringToCPD.invoke(null, nbt);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @Deprecated
    public ItemStack getItemStack(final Object cpd) {
        try {
            Object nmsItem = cpdToEi.invoke(null, cpd);
            Object craftItem = asBukkitCopy.invoke(null, nmsItem);

            return (ItemStack) craftItem;

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @Deprecated
    public ItemStack getItemStack(final String nbt) {
        try {
            Object cpd = stringToCPD.invoke(null, nbt);
            Object nmsItem = cpdToEi.invoke(null, cpd);
            Object craftItem = asBukkitCopy.invoke(null, nmsItem);

            return (ItemStack) craftItem;

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public void setEnvironment(
            final World world,
            final World.Environment environment
    ) {
        Object craftWorld = getCraftWorld(world);

        if (craftWorld == null) {
            return;
        }

        try {
            world_c_environment.set(craftWorld, environment);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public Object getWorldServer(final World world) {
        try {
            Object craftWorld = getCraftWorld(world);

            if (craftWorld == null) {
                return null;
            }

            return getNMSWorld.invoke(craftWorld);

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public Object getCraftWorld(final World world) {
        if (world == null || CraftWorldClass == null) {
            return null;
        }

        if (CraftWorldClass.isInstance(world)) {
            return CraftWorldClass.cast(world);
        }

        return null;
    }

    @SuppressWarnings("unused")
    public Object getWorld_e(final World world) {
        try {
            Object craftWorld = getCraftWorld(world);

            if (craftWorld == null) {
                return null;
            }

            return getNMSWorld.invoke(craftWorld);

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public Class<?> getWorldClass() {
        return WorldClass;
    }

    public Class<?> getWorldProviderClass() {
        return WorldProviderClass;
    }

    private String getMethodName(
            final ReflectiveMethod method
    ) {
        switch (method) {
            case NMSItemStack_save:
                if (Version.mcMainVersion <= 17) {
                    return "save";
                }
                return "b";

            case NMSItemStack_setTag:
                if (Version.mcMainVersion <= 17) {
                    return "setTag";
                }
                return "c";

            case MojangsonParser_parse:
                if (Version.mcMainVersion <= 17) {
                    return "parse";
                }
                return "a";

            case MojangsonParser_parseLiteral:
                if (Version.mcMainVersion == 12) {
                    return "c";
                } else if ("v1_13_R1".equals(Version.getVersion())) {
                    return "b";
                } else if (Version.mcMainVersion <= 17) {
                    return "parseLiteral";
                }
                return "b";

            case NBTTagCompound_set:
                if (Version.mcMainVersion <= 17) {
                    return "set";
                }
                return "a";

            default:
                throw new IllegalArgumentException(
                        "Неизвестный reflective method: " + method
                );
        }
    }

    private void init_NBTTagCompoundClass() {
        try {
            Class<?> craftMetaBlockStateClass =
                    ClassResolver.obc("inventory.CraftMetaBlockState");

            Field field = craftMetaBlockStateClass
                    .getDeclaredField("blockEntityTag");

            field.setAccessible(true);

            this.NBTTagCompoundClass = field.getType();
            return;

        } catch (ReflectiveOperationException ignored) {
            // Продолжаем поиск альтернативным способом.
        }

        if (CraftEntityClass == null) {
            return;
        }

        for (Method method : CraftEntityClass.getDeclaredMethods()) {
            if ("save".equals(method.getName())) {
                this.NBTTagCompoundClass = method.getReturnType();
                return;
            }
        }
    }

    private void init_NBTTagCompound_map() {
        if (NBTTagCompoundClass == null || unhandledTags == null) {
            return;
        }

        for (Field field : NBTTagCompoundClass.getDeclaredFields()) {
            if (field.getType() == unhandledTags.getType()) {
                this.NBTTagCompound_map = field;
                this.NBTTagCompound_map.setAccessible(true);
                return;
            }
        }
    }

    private void init_NBTBaseClass() {
        try {
            this.NBTBaseClass = Class.forName(
                    this.NBTTagCompoundClass
                            .getName()
                            .replace(
                                    ".NBTTagCompound",
                                    ".NBTBase"
                            )
            );
        } catch (ClassNotFoundException exception) {
            exception.printStackTrace();
        }
    }

    private enum ReflectiveMethod {
        NMSItemStack_save,
        NMSItemStack_setTag,
        MojangsonParser_parse,
        MojangsonParser_parseLiteral,
        NBTTagCompound_set
    }
}
