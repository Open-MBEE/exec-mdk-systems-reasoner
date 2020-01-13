package org.openmbee.mdk.systems_reasoner;

import com.nomagic.magicdraw.actions.ActionsConfiguratorsManager;
import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.plugins.PluginDescriptor;
import com.nomagic.magicdraw.plugins.PluginUtils;
import com.nomagic.magicdraw.uml.DiagramTypeConstants;

public class SystemsReasonerPlugin extends Plugin {
    private static String VERSION;

    public SystemsReasonerPlugin() {
        super();
    }

    public static String getVersion() {
        if (VERSION == null) {
            VERSION = PluginUtils.getPlugins().stream().map(Plugin::getDescriptor).filter(descriptor -> descriptor.getName().equals("MDK Systems Reasoner")).map(PluginDescriptor::getVersion).findAny().orElse(null);
        }
        return VERSION;
    }

    @Override
    public boolean close() {
        return true;
    }

    @Override
    public void init() {
        ActionsConfiguratorsManager acm = ActionsConfiguratorsManager.getInstance();
        SRConfigurator srConfigurator = new SRConfigurator();
        acm.addSearchBrowserContextConfigurator(srConfigurator);
        acm.addContainmentBrowserContextConfigurator(srConfigurator);
        acm.addBaseDiagramContextConfigurator(DiagramTypeConstants.UML_ANY_DIAGRAM, srConfigurator);
    }

    @Override
    public boolean isSupported() {
        return true;
    }
}
