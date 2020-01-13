package org.openmbee.mdk;

import com.nomagic.magicdraw.plugins.PluginUtils;
import org.openmbee.mdk.systems_reasoner.SystemsReasonerPlugin;
import gov.nasa.jpl.mbee.mdk.test.framework.ApplicationStartClassRunner;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(ApplicationStartClassRunner.class)
public class SystemsReasonerPluginTest {

    private static SystemsReasonerPlugin plugin;

    @BeforeClass
    public static void setup() {
        plugin = (SystemsReasonerPlugin) PluginUtils.getPlugins().stream().peek(plugin -> System.out.println(plugin.getDescriptor().getName())).filter(s -> s instanceof SystemsReasonerPlugin).findFirst().get();
    }

    @Test
    public void init() {
        assertTrue("Plugin shall be installed in MagicDraw.", plugin != null && plugin.getDescriptor().getName().equals("MDK Systems Reasoner"));
    }
}