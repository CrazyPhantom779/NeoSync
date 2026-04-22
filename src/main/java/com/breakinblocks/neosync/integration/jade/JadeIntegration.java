package com.breakinblocks.neosync.integration.jade;

import com.breakinblocks.neosync.common.block.ShellConstructorBlock;
import com.breakinblocks.neosync.common.block.ShellStorageBlock;
import com.breakinblocks.neosync.common.block.TreadmillBlock;
import snownee.jade.impl.WailaClientRegistration;

/**
 * Direct registration entry point. Invoked from {@link com.breakinblocks.neosync.client.SyncClientExtensions}
 * via {@code ModList.get().isLoaded("jade")} guard — bypasses Jade's annotation-scan discovery
 * which is unreliable in the ModDevGradle development environment.
 */
public final class JadeIntegration {
    private static boolean registered;

    private JadeIntegration() {}

    public static void register() {
        if (registered) return;
        registered = true;

        WailaClientRegistration registration = WailaClientRegistration.instance();
        registration.registerBlockComponent(ShellContainerComponentProvider.INSTANCE, ShellConstructorBlock.class);
        registration.registerBlockComponent(ShellContainerComponentProvider.INSTANCE, ShellStorageBlock.class);
        registration.registerBlockComponent(TreadmillComponentProvider.INSTANCE, TreadmillBlock.class);
    }
}
