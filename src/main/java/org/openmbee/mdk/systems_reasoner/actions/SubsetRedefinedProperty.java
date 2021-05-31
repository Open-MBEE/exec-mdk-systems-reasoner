package org.openmbee.mdk.systems_reasoner.actions;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.RedefinableElement;

import javax.annotation.CheckForNull;
import java.awt.event.ActionEvent;

public class SubsetRedefinedProperty extends SRAction {
    public static final String DEFAULT_ID = "Add Subsetted Property";
    private final Property subsetting;
    private final Property subsetted;

    public SubsetRedefinedProperty(Property subsetting, Property subsetted) {
        super(DEFAULT_ID);
        this.subsetting = subsetting;
        this.subsetted = subsetted;

    }

    @Override
    public void actionPerformed(@CheckForNull ActionEvent actionEvent) {
        subsetting.getSubsettedProperty().add(subsetted);
    }
}