package org.openmbee.mdk.systems_reasoner.actions;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import gov.nasa.jpl.mbee.mdk.validation.GenericRuleViolationAction;

import java.lang.Class;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreateSpecializedTypeAction extends GenericRuleViolationAction {
    public static final List<Class<? extends Classifier>> UNSPECIALIZABLE_CLASSIFIERS = new ArrayList<Class<? extends Classifier>>();

    static {
        UNSPECIALIZABLE_CLASSIFIERS.add(DataType.class);
        UNSPECIALIZABLE_CLASSIFIERS.add(PrimitiveType.class);
    }

    private static final String DEFAULT_NAME = "Create Specialized Classifier";

    private final Property property;
    private final Classifier parent;
    private final String name;
    private final boolean isIndividual;
    private final boolean isRecursive;
    private final boolean isMultiply;


    public CreateSpecializedTypeAction(final Property property, final Classifier parent, final String name, boolean isIndividual, boolean isRecursive, boolean isMultiply) {
        super(name);
        this.property = property;
        this.parent = parent;
        this.name = name;
        this.isIndividual = isIndividual;
        this.isRecursive = isRecursive;
        this.isMultiply = isMultiply;
    }

    public static void createSpecializedType(final Property property, final Classifier parent, boolean isIndividual, boolean isRecursive, boolean isMultiply) {
        createSpecializedType(property, parent, new ArrayList<>(), new ArrayList<>(), isIndividual, isRecursive, isMultiply);
    }

    public static boolean createSpecializedType(final StructuralFeature redefinedAttribute, final Classifier parent, final List<RedefinableElement> traveled, List<Classifier> visited, boolean isIndividual, boolean isRecursive, boolean isMultiply) {
        if (!parent.isEditable()) {
            Application.getInstance().getGUILog().log(parent.getQualifiedName() + " is not editable. Skipping creating specialization.");
            return true;
        }
        if (redefinedAttribute.getType() instanceof Classifier && !(redefinedAttribute.getType() instanceof Property)) {
            boolean hasTraveled = false;
            if (traveled.contains(redefinedAttribute)) {
                hasTraveled = true;
            }
            else {
                for (final RedefinableElement redefinedProperty : redefinedAttribute.getRedefinedElement()) {
                    if (traveled.contains(redefinedProperty)) {
                        hasTraveled = true;
                        break;
                    }
                }
            }
            if (hasTraveled) {
                Application.getInstance().getGUILog().log("[WARNING] Detected circular reference at " + redefinedAttribute.getQualifiedName() + ". Stopping recursion.");
                return false;
            }

            traveled.add(redefinedAttribute);
            traveled.addAll(redefinedAttribute.getRedefinedElement());

            final Classifier general = (Classifier) redefinedAttribute.getType();
            Type special = null;
            if (isIndividual || (isRecursive && getExistingSpecial(redefinedAttribute) == null)) {
                SpecializeStructureAction speca = new SpecializeStructureAction(general, false, "", isRecursive, isIndividual, isMultiply);
                special = speca.createSpecialClassifier(parent, new ArrayList<>(traveled), visited);
            }
            else if (getExistingSpecial(redefinedAttribute) != null) {
                special = getExistingSpecial(redefinedAttribute);
            }
            else if (visited.contains(general)) {
                Application.getInstance().getGUILog().log("[WARNING] Detected circular reference. Type  " + general.getQualifiedName() + " referenced by " + redefinedAttribute.getQualifiedName() + " was already visited. Stopping recursion.");
                return false;
            }

            if (special == null) {
                return true;
            }
            redefinedAttribute.setType(special);
        }
        return true;
    }

    private static Type getExistingSpecial(StructuralFeature structuralFeature) {
        Set<Type> types = new HashSet<>();
        Element owner = structuralFeature.getOwner();
        for (RedefinableElement redef : structuralFeature.getRedefinedElement()) {
            if (redef instanceof TypedElement) {
                types.add(((TypedElement) redef).getType());
            }
        }
        for (Element oe : owner.getOwnedElement()) {
            if (oe instanceof Property) {
                if (!oe.equals(structuralFeature)) {
                    for (RedefinableElement redef : ((Property) oe).getRedefinedElement()) {
                        if (redef instanceof TypedElement) {
                            if (types.contains(((TypedElement) redef).getType())) {
                                return ((Property) oe).getType();
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void run() {
        CreateSpecializedTypeAction.createSpecializedType(property, parent, isIndividual, isRecursive, isMultiply);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSessionName() {
        return "create specialized classifier";
    }
}
