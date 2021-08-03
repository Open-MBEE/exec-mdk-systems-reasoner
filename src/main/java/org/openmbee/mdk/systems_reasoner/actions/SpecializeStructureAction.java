package org.openmbee.mdk.systems_reasoner.actions;

import com.nomagic.magicdraw.copypaste.CopyPasting;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;
import com.nomagic.magicdraw.ui.dialogs.SelectElementInfo;
import com.nomagic.magicdraw.ui.dialogs.SelectElementTypes;
import com.nomagic.magicdraw.ui.dialogs.selection.ElementSelectionDlg;
import com.nomagic.magicdraw.ui.dialogs.selection.ElementSelectionDlgFactory;
import com.nomagic.magicdraw.ui.dialogs.selection.SelectionMode;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.magicdraw.auxiliaryconstructs.mdmodels.Model;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters;
import gov.nasa.jpl.mbee.mdk.util.Utils;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.Class;
import java.util.*;
import java.util.List;

public class SpecializeStructureAction extends SRAction {
    public static final List<Class<? extends Classifier>> UNSPECIALIZABLE_CLASSIFIERS = Arrays.asList(
            DataType.class,
            PrimitiveType.class
    );
    private static final List<Class<?>> CONTAINER_CLASSES = Arrays.asList(
            com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class.class,
            com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package.class,
            Model.class
    );

    private final Classifier classifier;
    private final boolean isValidationMode;
    private final boolean isRecursive;
    private final boolean isIndividual;
    private final boolean isMultiply;

    public SpecializeStructureAction(final Classifier classifier, boolean isValidationMode, String id, boolean isRecursive, boolean isIndividual, boolean isMultiply) {
        super(id, classifier);
        this.classifier = classifier;
        this.isValidationMode = isValidationMode;
        this.isRecursive = isRecursive;
        this.isIndividual = isIndividual;
        this.isMultiply = isMultiply;
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        final Frame dialogParent = MDDialogParentProvider.getProvider().getDialogParent();
        final ElementSelectionDlg dlg = ElementSelectionDlgFactory.create(dialogParent);
        dlg.setTitle("Select container for generated elements:");
        final SelectElementTypes set = new SelectElementTypes(null, CONTAINER_CLASSES, null, null);
        final SelectElementInfo sei = new SelectElementInfo(true, false, Application.getInstance().getProject().getModel().getOwner(), true);
        ElementSelectionDlgFactory.initSingle(dlg, set, sei, classifier.getOwner());


        dlg.setSelectionMode(SelectionMode.SINGLE_MODE);
        if (!isValidationMode) {
            dlg.setVisible(true);
        }
        if (isValidationMode || dlg.isOkClicked() && dlg.getSelectedElement() != null && dlg.getSelectedElement() instanceof Namespace) {
            SessionManager.getInstance().createSession("Create BST");
            Namespace container;
            if (isValidationMode) {
                container = (Namespace) classifier.getOwner();
            }
            else {
                container = (Namespace) dlg.getSelectedElement();
            }

            Classifier specific = createSpecialClassifier(container, new ArrayList<>(), new ArrayList<>());
            checkAssociationsForInheritance(specific, classifier);
            SessionManager.getInstance().closeSession();
        }
    }

    public Classifier createSpecialClassifier(Namespace container, List<RedefinableElement> traveled, List<Classifier> visited) {

        for (final Class<? extends Classifier> c : UNSPECIALIZABLE_CLASSIFIERS) {
            if (c.isAssignableFrom(classifier.getClass())) {
//                Application.getInstance().getGUILog()
//                        .log("[WARNING] " + (structuralFeature != null ? structuralFeature.getQualifiedName() : "< >") + " is a " + c.getSimpleName() + ", which is not specializable.");
                return null;
            }
        }

        Classifier specific = (Classifier) CopyPasting.copyPasteElement(classifier, container, true);
        if (specific == null) {
            Application.getInstance().getGUILog().log("[ERROR] Failed to create specialized classifier for " + Converters.getElementToHumanNameConverter().apply(classifier) + " in " + Converters.getElementToHumanNameConverter().apply(container) + ". Aborting specialization.");
            return null;
        }
        visited.add(specific);
        visited.add(classifier);

        // NOMAGIC-985 Model corruption `Relationship has no *client*` caused by Diagram copy-paste post-processing that clones Generalizations from the copied Diagram but conflicts with SR. Using specific.getGeneralization would be more proper, but Generalization#source attribute is missing. Monkey patch by force triggering Diagram post-processing and deleting.
        Set<Diagram> nestedDiagrams = collectDiagrams(specific, new HashSet<>());
        nestedDiagrams.stream().map(diagram -> Project.getProject(diagram).getDiagram(diagram)).filter(Objects::nonNull).forEach(DiagramPresentationElement::ensureLoaded);
        new HashSet<>(specific.getGeneralization()).forEach(BaseElement::dispose);

        Utils.createGeneralization(classifier, specific);

        ArrayList<RedefinableElement> redefinedElements = new ArrayList<>();
        for (NamedElement namedElement : specific.getOwnedMember()) {
            if (namedElement instanceof RedefinableElement && !((RedefinableElement) namedElement).isLeaf()) {
                redefinedElements.add((RedefinableElement) namedElement);
                ((RedefinableElement) namedElement).getRedefinedElement().clear();
            }
        }

        Set<NamedElement> members = new HashSet<>(specific.getInheritedMember());//
        //ClassifierHelper.collectInheritedAttributes(specific ,listOfAllMembers, false, true);
        List<NamedElement> removeElements = new ArrayList<>();
        for (NamedElement member : members) {
            if (member instanceof RedefinableElement) {
                Collection<RedefinableElement> redefinedBy = ((RedefinableElement) member).getRedefinedElement();
                removeElements.addAll(redefinedBy);
            }
            else {
                removeElements.add(member);
            }
        }
        members.removeAll(removeElements);

        List<RedefinableElement> elementsToBeRedefined = new ArrayList<>();
        for (NamedElement ne : members) { // Exclude Classifiers for now -> Should Aspect Blocks be Redefined?
            if (ne instanceof RedefinableElement && !((RedefinableElement) ne).isLeaf() && !(ne instanceof Classifier)) {
                final RedefinableElement elementToBeRedefined = (RedefinableElement) ne;
                RedefinableElement existingRedefiningElement = RedefineAttributeAction.findExistingRedefiningElement(specific, elementToBeRedefined);
                if (existingRedefiningElement != null) {
                    redefinedElements.remove(existingRedefiningElement);
                }
                if (RedefineAttributeAction.isNotRedefinable(specific, elementToBeRedefined)) {
                    continue;
                }
                if (isMultiply && elementToBeRedefined instanceof Property &&
                        ((Property) elementToBeRedefined).getSubsettedProperty().stream()
                                .anyMatch(subsetted -> RedefineAttributeAction.getExplicitMultiplicity(subsetted) > 1)) {
                    continue;
                }
                elementsToBeRedefined.add(elementToBeRedefined);
            }
        }

        for (RedefinableElement redefinedElement : redefinedElements) {
            redefinedElement.dispose();
        }

        for (RedefinableElement elementToBeRedefined : elementsToBeRedefined) {
            RedefineAttributeAction.redefineRedefinableElement(specific, elementToBeRedefined, traveled, visited, isIndividual, isRecursive, isMultiply);
        }

        return specific;
    }


    private <R extends Collection<Diagram>> R collectDiagrams(Namespace specific, R diagrams) {
        for (NamedElement ne : specific.getOwnedMember()) {
            if (ne instanceof Diagram) {
                diagrams.add((Diagram) ne);
            }
            else if (ne instanceof Namespace) {
                for (NamedElement nam : ((Namespace) ne).getOwnedMember()) {
                    if (nam instanceof Namespace) {
                        collectDiagrams((Namespace) nam, diagrams);
                    }
                }
            }
        }
        return diagrams;
    }


    private void checkAssociationsForInheritance(Classifier classifier, Classifier general) {
        if (classifier == null) {
            return;
        }
        assocRule:
        for (Element child : classifier.getOwnedElement()) {
            if (child instanceof Property) {
                Type partType = ((Property) child).getType();
                for (Element superChild : general.getOwnedElement()) {
                    if (superChild instanceof Property) {
                        Type superPartType = ((Property) superChild).getType();
                        if (partType != null) {
                            if (partType.equals(superPartType)) {
                                if (hasAnAssociation(superChild)) {
                                    if (hasInheritanceFromTo(((Property) child).getAssociation(), ((Property) superChild).getAssociation())) {
                                        break assocRule;
                                    }
                                    else {
                                        AddAssociationGeneralizationAction action = new AddAssociationGeneralizationAction(((Property) child).getAssociation(), ((Property) superChild).getAssociation());
                                        action.actionPerformed(null);
                                    }
                                }
                            }
                            else if (partType instanceof Classifier) {
                                if (((Classifier) partType).getGeneral().contains(superPartType)) {
                                    if (hasInheritanceFromTo(((Property) child).getAssociation(), ((Property) superChild).getAssociation())) {
                                        break assocRule;
                                    }
                                    else {
                                        AddAssociationGeneralizationAction action = new AddAssociationGeneralizationAction(((Property) child).getAssociation(), ((Property) superChild).getAssociation());
                                        action.actionPerformed(null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean hasAnAssociation(Element superChild) {
        return ((Property) superChild).getAssociation() != null;

    }

    private boolean hasInheritanceFromTo(Classifier classifier, Classifier general) {
        if (classifier != null) {
            return ModelHelper.getGeneralClassifiersRecursivelly(classifier).contains(general);
        }
        else {
            return false;
        }
    }

}
