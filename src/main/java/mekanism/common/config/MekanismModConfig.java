package mekanism.common.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Path;
import java.util.function.Function;
import mekanism.common.Mekanism;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Custom {@link ModConfig} implementation that allows for rerouting the server config from being in the worlds folder to being in the normal config folder. This allows
 * for us to use the built in sync support, without the extra hassle of having to explain to people where the config file is, or require people in single player to edit
 * the config each time they make a new world.
 */
public class MekanismModConfig extends ModConfig {

    private static final MekanismConfigFileTypeHandler MEK_TOML = new MekanismConfigFileTypeHandler();

    public MekanismModConfig(Type type, ForgeConfigSpec spec, ModContainer container, String fileName) {
        super(type, spec, container, Mekanism.MOD_NAME + "/" + fileName + ".toml");
    }

    @Override
    public ConfigFileTypeHandler getHandler() {
        return MEK_TOML;
    }

    private static class MekanismConfigFileTypeHandler extends ConfigFileTypeHandler {

        @Override
        public Function<ModConfig, CommentedFileConfig> reader(Path configBasePath) {
            //Intercept server config path reading for Mekanism configs and reroute it to the normal config directory
            if (configBasePath.endsWith("serverconfig")) {
                return super.reader(FMLPaths.CONFIGDIR.get());
            }
            return super.reader(configBasePath);
        }
    }
}