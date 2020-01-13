package org.openmbee.mdk.systems_reasoner.actions;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.InstanceSpecification;
import gov.nasa.jpl.mbee.mdk.util.Utils;
import org.openmbee.mdk.systems_reasoner.validation.SRValidationSuite;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidateAction extends SRAction {
    public static final String DEFAULT_ID = "Validate";
    public List<? extends Element> elements;

    public ValidateAction(Element element) {
        this(Collections.singletonList(element));
    }

    public ValidateAction(List<? extends Element> elements) {
        super(DEFAULT_ID);
        this.elements = elements;
    }


    public static void validate(final List<? extends Element> elements) {
        final List<Element> elems = new ArrayList<>(elements);
        final SRValidationSuite svs = new SRValidationSuite(elems);
        svs.run();
        Utils.displayValidationWindow(Application.getInstance().getProject(), svs, "Systems Reasoner Validation");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        validate(elements);
    }

    public static void validate(InstanceSpecification instance) {
        ArrayList<InstanceSpecification> insts = new ArrayList<>();
        insts.add(instance);
        validate(insts);
    }

}
