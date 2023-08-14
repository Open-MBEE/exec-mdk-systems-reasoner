package org.openmbee.mdk.systems_reasoner.actions;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import org.openmbee.mdk.validation.GenericRuleViolationAction;

public class SelectInContainmentTreeAction extends GenericRuleViolationAction {
    private static final String DEFAULT_NAME = "Select in Containment Tree";

    private Element element;

    public SelectInContainmentTreeAction(final Element element) {
        super(DEFAULT_NAME);
        this.element = element;
    }

    @Override
    public void run() {
        Application.getInstance().getMainFrame().getBrowser().getContainmentTree().openNode(element);
    }

    @Override
    public String getName() {
        return DEFAULT_NAME;
    }

    @Override
    public String getSessionName() {
        return DEFAULT_NAME;
    }
}
