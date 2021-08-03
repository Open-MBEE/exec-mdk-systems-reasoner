package org.openmbee.mdk.systems_reasoner.actions;

import com.nomagic.magicdraw.copypaste.CopyPasting;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.compositestructures.mdinternalstructures.Connector;
import com.nomagic.uml2.ext.magicdraw.metadata.UMLFactory;
import gov.nasa.jpl.mbee.mdk.util.Utils;
import gov.nasa.jpl.mbee.mdk.validation.GenericRuleViolationAction;
import org.openmbee.mdk.systems_reasoner.validation.SRValidationSuite;

import java.util.*;
import java.util.stream.IntStream;

public class RedefineAttributeAction extends GenericRuleViolationAction {
    private static final String DEFAULT_NAME = "Redefine Attribute";

    private final Classifier target;
    private final RedefinableElement redefinedAttribute;
    private final boolean isIndividual;
    private final boolean isRecursive;
    private final boolean isMultiply;

    public RedefineAttributeAction(Classifier target, RedefinableElement redefinedAttribute, boolean isIndividual, boolean isRecursive, boolean isMultiply) {
        this(target, redefinedAttribute, isIndividual, isRecursive, isMultiply, DEFAULT_NAME);
    }

    public RedefineAttributeAction(Classifier target, RedefinableElement redefinedAttribute, boolean isIndividual, boolean isRecursive, boolean isMultiply, String name) {
        super(name);

        this.target = target;
        this.redefinedAttribute = redefinedAttribute;
        this.isRecursive = isRecursive;
        this.isIndividual = isIndividual;
        this.isMultiply = isMultiply;
    }

    public static RedefinableElement redefineRedefinableElement(final Classifier target, final RedefinableElement redefinedAttribute, boolean isIndividual, boolean isRecursive, boolean isMultiply) {
        return redefineRedefinableElement(target, redefinedAttribute, new ArrayList<>(), new ArrayList<>(), isIndividual, isRecursive, isMultiply);
    }

    public static RedefinableElement redefineRedefinableElement(final Classifier target, final RedefinableElement redefinedAttribute, final List<RedefinableElement> traveled, List<Classifier> visited, boolean isIndividual, boolean isRecursive, boolean isMultiply) {
        if (isNotRedefinable(target, redefinedAttribute)) {
            return null;
        }

        RedefinableElement redefiningElement = findExistingRedefiningElement(target, redefinedAttribute);
        if (redefiningElement == null) {
            redefiningElement = (RedefinableElement) CopyPasting.copyPasteElement(redefinedAttribute, target, false);
            if (redefiningElement == null) {
                return null;
            }
        }
        redefiningElement.getRedefinedElement().removeAll(getRedefinedElementsRecursively(redefinedAttribute, new HashSet<>()));
        redefiningElement.getRedefinedElement().add(redefinedAttribute);

        if (redefinedAttribute instanceof Property) {
            if (((Property) redefinedAttribute).getAssociation() != null) {
                if (!existingAssociationInheritsFromGeneralAssociation(redefiningElement, (Property) redefinedAttribute)) {
                    createInheritingAssociation((Property) redefinedAttribute, target, (Property) redefiningElement);
                }
            }
        }
        if (redefiningElement instanceof Property && ((TypedElement) redefiningElement).getType() != null) {
            Property partProperty = (Property) redefiningElement;

            if (isRecursive) {
                CreateSpecializedTypeAction.createSpecializedType(partProperty, target, traveled, visited, isIndividual, true, isMultiply);
            }

            if (isMultiply) {
                int multiplicity = getExplicitMultiplicity(partProperty);
                if (multiplicity > 1) {
                    if (partProperty.get_propertyOfSubsettedProperty().isEmpty()) {
                        for (int i = 1; i <= multiplicity; i++) {
                            // find existing subsetting property in general for redefinition by name collision in the absence of a better technique
                            int finalI = i;
                            Property existingSubsettingProperty = redefinedAttribute instanceof Property && ((Property) redefinedAttribute).getClassifier() != null ?
                                    ((Property) redefinedAttribute).getClassifier().getMember().stream()
                                            .filter(m -> m instanceof Property)
                                            .map(m -> (Property) m)
                                            .filter(p -> p.getSubsettedProperty().contains(redefinedAttribute))
                                            .filter(p -> Objects.equals(p.getName(), partProperty.getName() + finalI))
                                            .findAny()
                                            .orElse(null) :
                                    null;
                            Property multipliedProperty = existingSubsettingProperty != null ?
                                    (Property) redefineRedefinableElement(target, existingSubsettingProperty, false, false, false) :
                                    (Property) CopyPasting.copyPasteElement(partProperty, target, false);
                            if (multipliedProperty != null) {
                                multipliedProperty.setName(partProperty.getName() + i);
                                multipliedProperty.getSubsettedProperty().clear();
                                multipliedProperty.getSubsettedProperty().add(partProperty);
                                multipliedProperty.getRedefinedElement().removeAll(getRedefinedElementsRecursively(redefinedAttribute, new HashSet<>()));
                                if (existingSubsettingProperty != null) {
                                    multipliedProperty.getRedefinedElement().add(existingSubsettingProperty);
                                }
                                // unset multiplicity to yield SysML's default multiplicity of 1
                                // ref https://www.omg.org/spec/SysML/1.5/PDF#8.3.1.1.9
                                multipliedProperty.setLowerValue(null);
                                multipliedProperty.setUpperValue(null);
                                if (isRecursive) {
                                    List<Classifier> copiedVisited = new ArrayList<>(visited);
                                    CreateSpecializedTypeAction.createSpecializedType(multipliedProperty, target, traveled, copiedVisited, isIndividual, true, true);
                                }
                            }
                        }
                    }
                    else {
                        Application.getInstance().getGUILog().log("[WARNING] " + partProperty.getQualifiedName() + " is already subsetted. Multiply skipped.");
                    }
                }
            }
        }

        return redefiningElement;
    }

    public static int getExplicitMultiplicity(Property property) {
        return property.getLower() == property.getUpper() ? property.getLower() : -1;
    }

    private static boolean existingAssociationInheritsFromGeneralAssociation(RedefinableElement redefinedElement, Property elementToBeRedefined) {
        if (redefinedElement instanceof Property) {
            Association association = ((Property) redefinedElement).getAssociation();
            Association general = elementToBeRedefined.getAssociation();
            if (association != null) {
                return eventuallyInherits(association, general);
            }
            else {
                return false;
            }

        }
        return false;
    }

    private static boolean eventuallyInherits(Classifier association, Classifier general) {
        if (association.getGeneral().contains(general)) {
            return true;
        }
        else {
            for (Classifier specific : association.getGeneral()) {
                if (eventuallyInherits(specific, general)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static RedefinableElement findExistingRedefiningElement(Classifier subClassifier, RedefinableElement elementToBeRedefined) {
        RedefinableElement existingRedefiningElement = null;
        for (NamedElement p : subClassifier.getOwnedMember()) {
            if (p instanceof RedefinableElement && SRValidationSuite.doesEventuallyRedefine((RedefinableElement) p, elementToBeRedefined)) {
                existingRedefiningElement = (RedefinableElement) p;
                break;
            }
            else if (p instanceof RedefinableElement) {
                if (isMatchingTypedElement((RedefinableElement) p, elementToBeRedefined)) {
                    existingRedefiningElement = (RedefinableElement) p;
                    break;
                }
                else if (p instanceof Connector && elementToBeRedefined instanceof Connector) {
                    Connector c1 = (Connector) p;
                    Connector c2 = (Connector) elementToBeRedefined;
                    if (c1.getEnd() == null || c2.getEnd() == null) {
                        continue;
                    }
                    if (c1.getEnd().size() != 2 || c2.getEnd().size() != 2) {
                        continue;
                    }
                    if (c1.getEnd().stream().anyMatch(e -> !(e.getRole() instanceof RedefinableElement)) || c2.getEnd().stream().anyMatch(e -> !(e.getRole() instanceof RedefinableElement))) {
                        continue;
                    }
                    if (IntStream.range(0, 2).anyMatch(i -> !isMatchingTypedElement((RedefinableElement) c1.getEnd().get(i).getRole(), (RedefinableElement) c2.getEnd().get(i).getRole()))) {
                        continue;
                    }
                    existingRedefiningElement = (RedefinableElement) p;
                    break;
                }
            }
        }
        return existingRedefiningElement;
    }

    public static boolean isNotRedefinable(Classifier subClassifier, RedefinableElement elementToBeRedefined) {
        if (elementToBeRedefined.isLeaf()) {
            Application.getInstance().getGUILog().log("[WARNING] " + elementToBeRedefined.getQualifiedName() + " is a leaf. Cannot redefine further.");
            return true;
        }
        if (!subClassifier.isEditable()) {
            Application.getInstance().getGUILog().log("[WARNING] " + subClassifier.getQualifiedName() + " is not editable. Skipping redefinition.");
            return true;
        }
        return false;
    }

    static void createInheritingAssociation(Property generalProperty, Classifier classifierOfnewProperty, Property newProperty) {
        Association generalAssociation = generalProperty.getAssociation();
        Association newAssociation = UMLFactory.eINSTANCE.createAssociation();
        newAssociation.setName(generalAssociation.getName());
        Property ownedEnd = UMLFactory.eINSTANCE.createProperty();
        ownedEnd.setOwner(newAssociation);
        ownedEnd.setType(classifierOfnewProperty);
        Utils.createGeneralization(generalAssociation, newAssociation);
        if (classifierOfnewProperty.getOwner() != null) {
            newAssociation.setOwner(classifierOfnewProperty.getOwner());
        }
        else {
            throw new NullPointerException("owner of classifier null!");
        }
        newAssociation.getMemberEnd().add(newProperty);
        newAssociation.getOwnedEnd().add(ownedEnd);
    }

    private static Set<RedefinableElement> getRedefinedElementsRecursively(RedefinableElement redefinableElement, Set<RedefinableElement> set) {
        if (set.add(redefinableElement)) {
            redefinableElement.getRedefinedElement().forEach(redefinedElement -> getRedefinedElementsRecursively(redefinedElement, set));
        }
        return set;
    }

    private static boolean isMatchingTypedElement(RedefinableElement p, RedefinableElement elementToBeRedefined) {
        Set<RedefinableElement> flattenedRedefinedElements = getRedefinedElementsRecursively(elementToBeRedefined, new HashSet<>());
        if (p.getRedefinedElement().stream().anyMatch(flattenedRedefinedElements::contains)) {
            return true;
        }
        if (p.getName().equals(elementToBeRedefined.getName())) {
            if (p instanceof TypedElement && elementToBeRedefined instanceof TypedElement) {
                if (((TypedElement) p).getType() != null) {
                    if (((TypedElement) p).getType().equals(((TypedElement) elementToBeRedefined).getType())) {
                        return true;
                    }
                }
                else if (((TypedElement) elementToBeRedefined).getType() == null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void run() {
        redefineRedefinableElement(target, redefinedAttribute, isIndividual, isRecursive, isMultiply);
    }

    @Override
    public String getSessionName() {
        return "redefine attribute";
    }
}
