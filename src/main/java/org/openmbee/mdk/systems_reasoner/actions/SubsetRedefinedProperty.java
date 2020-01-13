package org.openmbee.mdk.systems_reasoner.actions;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.RedefinableElement;

import javax.annotation.CheckForNull;
import java.awt.event.ActionEvent;

public class SubsetRedefinedProperty extends SRAction {
    public static final String DEFAULT_ID = "Set subsetted property in redefining property.";
    private final Property redefinedElement;
    private final Property redefiningElement;

    public SubsetRedefinedProperty(Property redefEl, Property redefingEl) {
        super(DEFAULT_ID);
        this.redefinedElement = redefEl;
        this.redefiningElement = redefingEl;

    }

    @Override
    public void actionPerformed(@CheckForNull ActionEvent actionEvent) {

        for (Property p : redefinedElement.getSubsettedProperty()) {
            for (RedefinableElement r : p.get_redefinableElementOfRedefinedElement()) {
                if (r instanceof Property) {
                    if (!redefiningElement.getSubsettedProperty().contains(r)) {
                        redefiningElement.getSubsettedProperty().add((Property) r);
                    }
                }
            }
        }
    }
}