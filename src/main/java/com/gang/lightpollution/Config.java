package com.gang.lightpollution;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Installation-local documentation preferences, not the unused Forge MDK example config. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.EnumValue<ConfigCommentLanguage> COMMENT_LANGUAGE = ConfigComments
            .bilingual(BUILDER, "commentLanguage",
                    "Language of generated TOML descriptions. AUTO follows the client's game language (zh_* and lzh = Chinese, others = English); a dedicated server uses bilingual comments. Explicit choices affect this installation only, never gameplay values.",
                    "TOML 注释语言。AUTO 跟随客户端游戏语言（zh_* 与 lzh 使用中文，其余使用英文）；专用服务器使用双语。显式 EN_US、ZH_CN、BILINGUAL 仅影响本机介绍，不修改玩法数值。")
            .defineEnum("commentLanguage", ConfigCommentLanguage.AUTO);
    public static final ForgeConfigSpec SPEC = ConfigComments.build(BUILDER);

    private Config() {}

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC && !(event instanceof ModConfigEvent.Unloading)) {
            ConfigLocalization.setMode(COMMENT_LANGUAGE.get());
        }
    }
}
