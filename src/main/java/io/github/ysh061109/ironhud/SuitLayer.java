package io.github.ysh061109.ironhud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Third-person armored suit. Your own player model is overlaid with a red/gold plated shell that has an
 * arc reactor, faceplate eye slits, palm and boot repulsors that glow (and flare when a TACZ gun fires), and
 * the other guns from your hotbar are holstered on the suit's back. Purely visual and client-side.
 */
public class SuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(IronHudMod.MODID, "textures/suit/suit.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(IronHudMod.MODID, "textures/suit/suit_glow.png");
    private final HumanoidModel<AbstractClientPlayer> shell;

    public SuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.shell = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing,
                       float limbSwingAmount, float pt, float age, float headYaw, float headPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (p != mc.player || p.isInvisible() || !HudConfig.SUIT_MODEL.get() || !HudState.enabled) return;
        getParentModel().copyPropertiesTo(shell);
        shell.hat.visible = false;
        shell.setAllVisible(true);
        shell.hat.visible = false;
        shell.head.copyFrom(getParentModel().head);
        shell.body.copyFrom(getParentModel().body);
        shell.rightArm.copyFrom(getParentModel().rightArm);
        shell.leftArm.copyFrom(getParentModel().leftArm);
        shell.rightLeg.copyFrom(getParentModel().rightLeg);
        shell.leftLeg.copyFrom(getParentModel().leftLeg);

        ps.pushPose();
        ps.scale(1.02f, 1.012f, 1.02f);
        shell.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, -1);
        float fl = TaczCompat.flash(pt);
        float pulse = 0.78f + 0.22f * Mth.sin((HudState.age + pt) * 0.25f) + fl * 0.6f;
        int v = Mth.clamp((int) (255 * Math.min(1f, pulse)), 0, 255);
        int argb = (255 << 24) | (v << 16) | (v << 8) | v;
        shell.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
        ps.popPose();

        if (HudConfig.SUIT_HOLSTER.get()) holster(ps, buf, light, p);
    }

    /** Hotbar guns other than the one in hand ride on the suit's back, crossed like backup weapons. */
    private void holster(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p) {
        int n = 0;
        for (int i = 0; i < 9 && n < 2; i++) {
            if (i == p.getInventory().selected) continue;
            ItemStack st = p.getInventory().items.get(i);
            if (!TaczCompat.isGun(st)) continue;
            ps.pushPose();
            try {
                getParentModel().body.translateAndRotate(ps);
                ps.translate(0.0f, 0.12f + n * 0.1f, 0.3f);
                ps.mulPose(Axis.XP.rotationDegrees(180f));
                ps.mulPose(Axis.ZP.rotationDegrees(n == 0 ? 52f : -52f));
                ps.mulPose(Axis.YP.rotationDegrees(90f));
                ps.scale(0.62f, 0.62f, 0.62f);
                Minecraft.getInstance().getItemRenderer().renderStatic(st, ItemDisplayContext.FIXED, light,
                        OverlayTexture.NO_OVERLAY, ps, buf, p.level(), p.getId() + i);
            } catch (RuntimeException ignored) {
                // a gun renderer that refuses this context simply is not shown
            }
            ps.popPose();
            n++;
        }
    }
}
