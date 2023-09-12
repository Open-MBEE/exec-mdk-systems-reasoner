package org.openmbee.mdk.systems_reasoner.actions;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Association;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Generalization;
import com.nomagic.uml2.ext.magicdraw.metadata.UMLFactory;

import java.awt.event.ActionEvent;

public class AddAssociationGeneralizationAction extends SRAction {
    public static final String DEFAULT_ID = "Generalize Association";
    private final Association association;
    private final Association superAssociation;

    public AddAssociationGeneralizationAction(Association association, Association superAssociation) {
        super(DEFAULT_ID);
        this.association = association;
        this.superAssociation = superAssociation;

    }

    public void actionPerformed(ActionEvent e) {
        if (association == null) {
            Application.getInstance().getGUILog().log("[WARNING] Association on specific missing. Skipping association generalization.");
            return;
        }
        if (superAssociation == null) {
            Application.getInstance().getGUILog().log("[WARNING] Association on general missing. Skipping association generalization.");
            return;
        }
        SessionManager.getInstance().createSession(DEFAULT_ID);
        try {
            Generalization gen = UMLFactory.eINSTANCE.createGeneralization();
            gen.setGeneral(superAssociation);
            gen.setSpecific(association);
            this.association.getGeneralization().add(gen);
        } finally {
            SessionManager.getInstance().closeSession();
        }
    }
}
