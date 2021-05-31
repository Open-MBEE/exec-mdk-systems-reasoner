package org.openmbee.mdk.systems_reasoner.actions;

import au.com.bytecode.opencsv.CSVReader;
import com.nomagic.actions.NMAction;
import com.nomagic.magicdraw.copypaste.CopyPasting;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.GUILog;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;
import com.nomagic.magicdraw.ui.dialogs.SelectElementInfo;
import com.nomagic.magicdraw.ui.dialogs.SelectElementTypes;
import com.nomagic.magicdraw.ui.dialogs.selection.ElementSelectionDlg;
import com.nomagic.magicdraw.ui.dialogs.selection.ElementSelectionDlgFactory;
import com.nomagic.magicdraw.ui.dialogs.selection.SelectionMode;
import com.nomagic.uml2.ext.magicdraw.auxiliaryconstructs.mdmodels.Model;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.metadata.UMLFactory;
import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters;
import gov.nasa.jpl.mbee.mdk.util.Utils;
import gov.nasa.jpl.mbee.mdk.validation.ValidationRule;
import gov.nasa.jpl.mbee.mdk.validation.ValidationRuleViolation;
import org.openmbee.mdk.systems_reasoner.validation.SRValidationSuite;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class ImportCSVAction extends SRAction {
    public static final String DEFAULT_ID = "Import from CSV";

    private static final String LITERAL_BOOLEAN_ID = "_16_5_1_12c903cb_1245415335546_39033_4086";
    private static final String LITERAL_INTEGER_ID = "_16_5_1_12c903cb_1245415335546_8641_4088";
    private static final String LITERAL_REAL_ID = "_11_5EAPbeta_be00301_1147431819399_50461_1671";
    private static final String LITERAL_STRING_ID = "_16_5_1_12c903cb_1245415335546_479030_4092";
    private static final char CSV_SEPARATOR = ',';

    private Classifier classifier;
    private int row;
    private Namespace container;

    public ImportCSVAction(Classifier classifier) {
        super(DEFAULT_ID);
        this.classifier = classifier;
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        final List<java.lang.Class<?>> types = new ArrayList<>();
        types.add(com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class.class);
        types.add(com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package.class);
        types.add(Model.class);


        GUILog gl = Application.getInstance().getGUILog();
        row = 0;
        JFileChooser choose = new JFileChooser();
        choose.setDialogTitle("Open CSV file");
        choose.setFileSelectionMode(JFileChooser.FILES_ONLY);
        HashSet<Classifier> createdElements = new HashSet<>();
        int retval = choose.showOpenDialog(null);
        if (retval == JFileChooser.APPROVE_OPTION) {
            if (choose.getSelectedFile() != null) {
                File savefile = choose.getSelectedFile();
                try {

                    final Frame dialogParent = MDDialogParentProvider.getProvider().getDialogParent();
                    final ElementSelectionDlg dlg = ElementSelectionDlgFactory.create(dialogParent);
                    dlg.setTitle("Select container for generated elements:");
                    final SelectElementTypes set = new SelectElementTypes(null, types, null, null);
                    final SelectElementInfo sei = new SelectElementInfo(true, false, Application.getInstance().getProject().getModel().getOwner(), true);
                    ElementSelectionDlgFactory.initSingle(dlg, set, sei, classifier.getOwner());
                    dlg.setSelectionMode(SelectionMode.SINGLE_MODE);
                    dlg.setVisible(true);
                    if (dlg.isOkClicked() && dlg.getSelectedElement() != null && dlg.getSelectedElement() instanceof Namespace) {
                        container = (Namespace) dlg.getSelectedElement();
                        SessionManager.getInstance().createSession("change");
                        gl.log("[INFO] Starting CSV import.");
                        CSVReader reader = new CSVReader(new FileReader(savefile), CSV_SEPARATOR);
                        importFromCsv(reader, createdElements);
                        checkForRedefinedElements(createdElements);
                        reader.close();
                        SessionManager.getInstance().closeSession();
                        checkForAssociationInheritance(createdElements);
                        gl.log("[INFO] CSV import finished.");
                    }
                } catch (IOException ex) {
                    gl.log("[ERROR] CSV import failed. Reason: " + ex.getMessage());
                    SessionManager.getInstance().cancelSession();
                    ex.printStackTrace();
                    for (StackTraceElement s : ex.getStackTrace()) {
                        gl.log("\t" + s.toString());
                    }
                }
            }
        }
    }


    private void checkForAssociationInheritance(HashSet<Classifier> createdElements) {

        for (Classifier element : createdElements) {
            for (Classifier general : element.getGeneral()) {
                SRValidationSuite.checkAssociationGeneralizations(element, general);
                ValidationRule ele = SRValidationSuite.getAssociationGeneralizationRule();
                for (ValidationRuleViolation violation : ele.getViolations()) {
                    NMAction action = violation.getActions().get(0);
                    action.actionPerformed(null);
                }
            }
        }

    }

    private void checkForRedefinedElements(HashSet<Classifier> createdElements) {
        for (Classifier ns : createdElements) {
            for (NamedElement mem : ns.getInheritedMember()) {
                if (mem instanceof RedefinableElement) {
                    boolean redefined = false;
                    for (NamedElement om : ns.getOwnedMember()) {
                        if (om instanceof RedefinableElement) {
                            if (SRValidationSuite.doesEventuallyRedefine(((RedefinableElement) om), (RedefinableElement) mem)) {
                                redefined = true;
                            }
                        }
                    }
                    if (!redefined) {
                        RedefineAttributeAction action = new RedefineAttributeAction(ns, (RedefinableElement) mem, false, false, false);
                        action.run();
                    }
                }
            }
        }
    }

    private void importFromCsv(CSVReader reader, HashSet<Classifier> createdElements) throws IOException {
        String[] line = reader.readNext(); // ignore header

        String selectedClassifierName = classifier.getName();
        List<Element> sortedColumns = new ArrayList<>();
        boolean isFirstLine = true;
        boolean lineWasEmpty = true;
        int elementName = -1;
        HashMap<Property, Classifier> thisLinesClasses = new HashMap<>();
        while (line != null) {
            if (!emptyLine(line)) {
                HashMap<Property, Classifier> previousLinesClasses = thisLinesClasses;
                thisLinesClasses = new HashMap<>();
                if (!isFirstLine) {
                    for (int jj = 0; jj < sortedColumns.size(); jj++) {
                        String valueFromCSV = line[jj].trim();
                        Element el = sortedColumns.get(jj);
                        if (el != null) {
                            if (el instanceof Property) {
                                Classifier child;
                                Type type = ((Property) el).getType();
                                if (!(type instanceof DataType)) {
                                    if (!valueFromCSV.isEmpty()) {
                                        if (type instanceof Classifier) {
                                            child = createNewSubElement(line, jj, (Classifier) type);
                                            thisLinesClasses.put((Property) el, child);
                                        }
                                    }
                                    else {
                                        thisLinesClasses.put((Property) el, previousLinesClasses.get(el));
                                    }
                                }
                                if (!valueFromCSV.isEmpty()) {
                                    setPropertyValue(valueFromCSV, (RedefinableElement) el, thisLinesClasses);
                                }
                            }
                        }
                        else {
                            if (sortedColumns.get(jj) == null) {
                                if (!line[jj].isEmpty()) {
                                    Classifier topClass = (Classifier) CopyPasting.copyPasteElement(classifier, container, true);
                                    if (topClass == null) {
                                        continue;
                                    }
                                    topClass.getOwnedMember().clear();
                                    topClass.getGeneralization().clear();
                                    Utils.createGeneralization(classifier, topClass);
                                    topClass.setName(line[jj]);
                                    thisLinesClasses.put(null, topClass);
                                    Application.getInstance().getGUILog().log("[INFO] Creating new " + classifier.getName() + " named " + topClass.getName() + " for line " + row + ".");
                                }
                                else {
                                    if (jj == elementName) {
                                        thisLinesClasses.put(null, previousLinesClasses.get(null));
                                    }
                                }
                            }
                        }
                    }

                }
                else {
                    for (int c = 0; c < line.length; c++) {
                        String propertyName = line[c];
                        if (!propertyName.isEmpty()) {
                            if (propertyName.contains(".")) {
                                propertyName = handleSubProperty(sortedColumns, propertyName, classifier);
                            }
                            else {
                                lineWasEmpty = false;
                                sortedColumns.add(getPropertyFromColumnName(propertyName, classifier));
                                if (propertyName.equals(selectedClassifierName)) {
                                    elementName = c;
                                }
                            }
                        }
                        else {
                            if (elementName == -1) {// only set it the first time.
                                elementName = c;
                            }
                            sortedColumns.add(null);
                        }
                    }
                }
                if (!lineWasEmpty) {
                    isFirstLine = false;
                }
                createdElements.addAll(thisLinesClasses.values());

            }
            line = reader.readNext();
            row++;
        }
    }

    private boolean emptyLine(String[] line) {
        for (String s : line) {
            if (!s.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String handleSubProperty(List<Element> sortedColumns, String propertyName, Classifier parent) {
        String[] subprops = propertyName.split("\\.");
        propertyName = propertyName.replace(subprops[0] + ".", "");
        String newTypeName = subprops[0];
        Element property = getPropertyFromColumnName(newTypeName, parent);
        if (property instanceof TypedElement) {
            Type type = ((TypedElement) property).getType();
            if (type instanceof Classifier) {
                if (propertyName.contains(".")) {
                    handleSubProperty(sortedColumns, propertyName, (Classifier) type);
                }
                else {
                    sortedColumns.add(getPropertyFromColumnName(propertyName, (Classifier) type));
                }
            }
        }
        return propertyName;
    }

    private Classifier createNewSubElement(String[] line, int index, Classifier generalClassifier) {
        Classifier createdClassifier = (Classifier) CopyPasting.copyPasteElement(generalClassifier, container, true);
        if (createdClassifier == null) {
            return null;
        }
        createdClassifier.getOwnedMember().clear();
        createdClassifier.getGeneralization().clear();
        Utils.createGeneralization(generalClassifier, createdClassifier);
        createdClassifier.setName(line[index]);
        Application.getInstance().getGUILog().log("[INFO] Creating new " + generalClassifier.getName() + " named " + createdClassifier.getName() + " for line " + row + ".");
        return createdClassifier;
    }

    private void setPropertyValue(String valueFromCSV, RedefinableElement el, HashMap<Property, Classifier> thisLinesClasses) {
        if (el instanceof TypedElement) {
            if (el.getOwner() instanceof Classifier) {
                Classifier owner = findMatchingSubclass((Classifier) el.getOwner(), thisLinesClasses.values());
                if (owner != null) {
                    Property prop = UMLFactory.eINSTANCE.createProperty();
                    prop.setType(((TypedElement) el).getType());
                    prop.getRedefinedElement().add(el);
                    prop.setOwner(owner);
                    prop.setName(el.getName());
                    ValueSpecification vs = null;
                    Classifier linkedElement = null;
                    if (((TypedElement) el).getType() instanceof Classifier) {
                        linkedElement = findMatchingSubclass((Classifier) ((TypedElement) el).getType(), thisLinesClasses.values());
                    }
                    if (linkedElement == null) {
                        if (((Property) el).getType() instanceof DataType) {
                            if (!valueFromCSV.isEmpty()) {
                                try {
                                    switch (Converters.getElementToIdConverter().apply(((Property) el).getType())) {
                                        case LITERAL_STRING_ID:
                                            LiteralString literalString = UMLFactory.eINSTANCE.createLiteralString();
                                            literalString.setValue(valueFromCSV);
                                            vs = literalString;
                                            break;
                                        case LITERAL_REAL_ID:
                                            LiteralReal literalReal = UMLFactory.eINSTANCE.createLiteralReal();
                                            literalReal.setValue(Double.parseDouble(valueFromCSV));
                                            vs = literalReal;
                                            break;
                                        case LITERAL_INTEGER_ID:
                                            LiteralInteger literalInteger = UMLFactory.eINSTANCE.createLiteralInteger();
                                            literalInteger.setValue(Integer.parseInt(valueFromCSV));
                                            vs = literalInteger;
                                            break;
                                        case LITERAL_BOOLEAN_ID:
                                            LiteralBoolean literalBoolean = UMLFactory.eINSTANCE.createLiteralBoolean();
                                            literalBoolean.setValue(Boolean.parseBoolean(valueFromCSV));
                                            vs = literalBoolean;
                                            break;
                                    }
                                } catch (NumberFormatException nf) {
                                    Application.getInstance().getGUILog().log("[WARNING] Value in line " + row + " for property " + el.getName() + " not correct. Reason: " + nf.getMessage());
                                    nf.printStackTrace();
                                }
                            }
                        }
                        if (vs != null) {
                            prop.setDefaultValue(vs);
                        }
                    }
                    else {
                        prop.setType(linkedElement);
                        if (el instanceof Property) {
                            if (((Property) el).getAssociation() != null) {
                                RedefineAttributeAction.createInheritingAssociation((Property) el, owner, prop);
                            }
                        }
                    }
                }
                else {
                    Application.getInstance().getGUILog().log("[WARNING] Property for " + el.getName() + " not created.");

                }
            }
        }

    }

    private Classifier findMatchingSubclass(Classifier general, Collection<Classifier> thisLinesClasses) {
        for (Classifier cl : thisLinesClasses) {
            if (cl != null) {
                if (cl.getGeneral().contains(general)) {
                    return cl;
                }
            }
        }
        return null;
    }

    /**
     * Selects in order owned elements, inherited elements and then ignores case.
     *
     * @param propertyName
     */
    private Element getPropertyFromColumnName(String propertyName, Classifier classifier) {
        for (Element p : classifier.getOwnedMember()) {
            if (p instanceof NamedElement) {
                if (propertyName.trim().equals(((NamedElement) p).getName())) {
                    return p;
                }
            }
        }
        for (Element p : classifier.getInheritedMember()) {
            if (p instanceof NamedElement) {
                if (propertyName.trim().equals(((NamedElement) p).getName())) {
                    return p;

                }
            }
        }
        for (Element p : classifier.getOwnedMember()) {
            if (p instanceof NamedElement) {
                if (propertyName.trim().equalsIgnoreCase(((NamedElement) p).getName())) {
                    return p;

                }
            }
        }
        for (Element p : classifier.getInheritedMember()) {
            if (p instanceof NamedElement) {
                if (propertyName.trim().equalsIgnoreCase(((NamedElement) p).getName())) {
                    return p;
                }
            }
        }
        return null;
    }
}