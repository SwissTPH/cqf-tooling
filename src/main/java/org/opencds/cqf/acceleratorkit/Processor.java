package org.opencds.cqf.acceleratorkit;

import ca.uhn.fhir.context.FhirContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.Operation;
import org.opencds.cqf.terminology.SpreadsheetHelper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by Bryn on 8/18/2019.
 */
public class Processor extends Operation {
    private String pathToSpreadsheet; // -pathtospreadsheet (-pts)
    private String encoding = "json"; // -encoding (-e)

    // Data Elements
    private String dataElementPages; // -dataelementpages (-dep) comma-separated list of the names of pages in the workbook to be processed

    // Canonical Base
    private String canonicalBase = "http://fhir.org/guides/who/anc-cds"; // -canonicalbase (-cb) (without trailing slash, e.g. http://fhir.org/guides/who/anc-cds)

    private String openMRSSystem = "http://openmrs.org/concepts";
    private Map<String, String> supportedCodeSystems = new HashMap<String, String>();

    private Map<String, DictionaryElement> elementMap = new HashMap<String, DictionaryElement>();
    private List<StructureDefinition> profiles = new ArrayList<StructureDefinition>();
    private List<CodeSystem> codeSystems = new ArrayList<CodeSystem>();
    private List<ValueSet> valueSets = new ArrayList<ValueSet>();
    private List<String> igJsonFragments = new ArrayList<String>();
    private List<String> igResourceFragments = new ArrayList<String>();



    @Override
    public void execute(String[] args) {
        setOutputPath("src/main/resources/org/opencds/cqf/acceleratorkit/output"); // default

        for (String arg : args) {
            if (arg.equals("-ProcessAcceleratorKit")) continue;
            String[] flagAndValue = arg.split("=");
            if (flagAndValue.length < 2) {
                throw new IllegalArgumentException("Invalid argument: " + arg);
            }
            String flag = flagAndValue[0];
            String value = flagAndValue[1];

            switch (flag.replace("-", "").toLowerCase()) {
                case "outputpath": case "op": setOutputPath(value); break; // -outputpath (-op)
                case "pathtospreadsheet": case "pts": pathToSpreadsheet = value; break;
                case "encoding": case "e": encoding = value.toLowerCase(); break;
                case "dataelementpages": case "dep": dataElementPages = value; break;
                case "canonicalbase": case "cb": canonicalBase = value; break;
                default: throw new IllegalArgumentException("Unknown flag: " + flag);
            }
        }

        if (pathToSpreadsheet == null) {
            throw new IllegalArgumentException("The path to the spreadsheet is required");
        }

        supportedCodeSystems.put("OpenMRS", openMRSSystem);
        supportedCodeSystems.put("ICD-10-WHO", "http://hl7.org/fhir/sid/icd-10");
        supportedCodeSystems.put("SNOMED-CT", "http://snomed.info/sct");
        supportedCodeSystems.put("LOINC", "http://loinc.org");
        supportedCodeSystems.put("RxNorm", "http://www.nlm.nih.gov/research/umls/rxnorm");

        Workbook workbook = SpreadsheetHelper.getWorkbook(pathToSpreadsheet);

        for (String page : dataElementPages.split(",")) {
            processDataElementPage(workbook, page);
        }

        processElementMap();
        writeProfiles();
        writeCodeSystems();
        writeValueSets();
        writeIgJsonFragments();
        writeIgResourceFragments();
    }


    private DictionaryCode getTerminologyCode(String codeSystemKey, String label, Row row, HashMap<String, Integer> colIds) {
        String system = supportedCodeSystems.get(codeSystemKey);
        String codeValue = SpreadsheetHelper.getCellAsString(row, getColId(colIds, codeSystemKey));
        String display = String.format("%s (%s)", label, codeSystemKey);
        if (codeValue != null && !codeValue.isEmpty()) {
            return getCode(system, label, display, codeValue, null);
        }
        return null;
    }

    private DictionaryCode getFhirCode(String label, Row row, HashMap<String, Integer> colIds) {
        String system = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "FhirCodeSystem"));
        String display = String.format("%s (%s)", label, "FHIR");
        if (system != null && !system.isEmpty()) {
            String codeValue = SpreadsheetHelper.getCellAsString(row, getColId(colIds,"FhirR4Code"));
            if (codeValue != null && !codeValue.isEmpty()) {
                return getCode(system, label, display, codeValue, null);
            }
        }
        return null;
    }

    private DictionaryCode getOpenMRSCode(String label, Row row, HashMap<String, Integer> colIds) {
        String system = openMRSSystem;
        String parent = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "OpenMRSEntityParent"));
        String display = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "OpenMRSEntity"));
        String codeValue = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "OpenMRSEntityId"));
        if (codeValue != null && !codeValue.isEmpty()) {
            return getCode(system, display, label, codeValue, parent);
        }
        return null;
    }

    private DictionaryCode getPrimaryCode(String label, Row row, HashMap<String, Integer> colIds) {
        DictionaryCode code;
        code = getOpenMRSCode(label, row, colIds);
        if (code == null) {
            code = getFhirCode(label, row, colIds);
        }
        if (code == null) {
            for (String codeSystemKey : supportedCodeSystems.keySet()) {
                code = getTerminologyCode(codeSystemKey, label, row, colIds);
                if (code != null) {
                    break;
                }
            }
        }
        return code;
    }

    private DictionaryCode getCode(String system, String label, String display, String codeValue, String parent) {
        DictionaryCode code = new DictionaryCode();
        code.setLabel(label);
        code.setSystem(system);
        code.setDisplay(display);
        code.setCode(codeValue);
        code.setParent(parent);
        return code;
    }

    private DictionaryFhirType getFhirType(Row row, HashMap<String, Integer> colIds) {
        DictionaryFhirType fhirType = new DictionaryFhirType();
        String resource = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "FhirR4Resource"));
        if (resource != null && !resource.isEmpty()) {
            fhirType.setResource(resource);
            fhirType.setBaseProfile(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "FhirR4BaseProfile")));
            fhirType.setVersion(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "FhirR4VersionNumber")));
        }
        return fhirType;
    }

    private int getColId(HashMap<String, Integer> colIds, String colName) {
        if (colIds.containsKey(colName)) {
            return colIds.get(colName);
        }

        return -1;
    }

    private void processDataElementPage(Workbook workbook, String page) {
        Sheet sheet = workbook.getSheet(page);
        Iterator<Row> it = sheet.rowIterator();
        HashMap<String, Integer> colIds = new HashMap<String, Integer>();
        DictionaryElement currentElement = null;
        String currentGroup = null;
        while (it.hasNext()) {
            Row row = it.next();
            int headerRow = 1;
            // Skip rows prior to header row
            if (row.getRowNum() < headerRow) {
                continue;
            }
            // Create column id map
            else if (row.getRowNum() == headerRow) {
                Iterator<Cell> colIt = row.cellIterator();
                while (colIt.hasNext()) {
                    Cell cell = colIt.next();
                    String header = SpreadsheetHelper.getCellAsString(cell).toLowerCase();
                    switch (header) {
                        case "data element label": colIds.put("Label", cell.getColumnIndex()); break;
                        // no group column in old or new spreadsheet? Ask Bryn?
                        //case "group": colIds.put("Group", cell.getColumnIndex()); break;
                        case "data element name": colIds.put("Name", cell.getColumnIndex()); break;
                        case "due": colIds.put("Due", cell.getColumnIndex()); break;
                        // no frequency column in new master spreadsheet?
                        //case "frequency": colIds.put("Due", cell.getColumnIndex()); break;
                        // relevance not used in FHIR?
                        //case "relevance": colIds.put("Relevance", cell.getColumnIndex()); break;
                        // info icon not used in FHIR?
                        //case "info icon": colIds.put("InfoIcon", cell.getColumnIndex()); break;
                        case "description": colIds.put("Description", cell.getColumnIndex()); break;
                        case "notes": colIds.put("Notes", cell.getColumnIndex()); break;
                        case "data type": colIds.put("Type", cell.getColumnIndex()); break;
                        case "input options": colIds.put("Choices", cell.getColumnIndex()); break;
                        case "calculation": colIds.put("Calculation", cell.getColumnIndex()); break;
                        case "validation required": colIds.put("Constraint", cell.getColumnIndex()); break;
                        case "required": colIds.put("Required", cell.getColumnIndex()); break;
                        case "editable": colIds.put("Editable", cell.getColumnIndex()); break;
                        case "openmrs entity parent": colIds.put("OpenMRSEntityParent", cell.getColumnIndex()); break;
                        case "openmrs entity": colIds.put("OpenMRSEntity", cell.getColumnIndex()); break;
                        case "openmrs entity id": colIds.put("OpenMRSEntityId", cell.getColumnIndex()); break;

                        // fhir resource details
                        case "hl7 fhir r4 - resource": colIds.put("FhirR4Resource", cell.getColumnIndex()); break;
                        case "hl7 fhir r4 - base profile": colIds.put("FhirR4BaseProfile", cell.getColumnIndex()); break;
                        case "hl7 fhir r4 - version number": colIds.put("FhirR4VersionNumber", cell.getColumnIndex()); break;

                        // terminology
                        case "fhir code system": colIds.put("FhirCodeSystem", cell.getColumnIndex()); break;
                        case "hl7 fhir r4 code": colIds.put("FhirR4Code", cell.getColumnIndex()); break;
                        case "icd-10-who": colIds.put("ICD-10-WHO", cell.getColumnIndex()); break;
                        case "snomed-ct": colIds.put("SNOMED-CT", cell.getColumnIndex()); break;
                        case "loinc": colIds.put("LOINC", cell.getColumnIndex()); break;
                        case "rxnorm": colIds.put("RxNorm", cell.getColumnIndex()); break;
                    }
                }
                continue;
            }

            String label = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Label"));
            String type = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Type"));
            String name = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Name"));
            if (name == null || name.isEmpty())
            {
                if (currentElement != null) {
                    String choices = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Choices"));
                    if (choices != null && !choices.isEmpty()) {
                        // Open MRS choices
                        DictionaryCode code = getOpenMRSCode(choices, row, colIds);
                        if (code != null) {
                            currentElement.getChoices().add(code);
                        }

                        // FHIR choices
                        String fhirCodeSystem = SpreadsheetHelper.getCellAsString(row, getColId(colIds, "FhirCodeSystem"));
                        if (fhirCodeSystem != null && !fhirCodeSystem.isEmpty()) {
                            code = getFhirCode(choices, row, colIds);
                            if (code != null) {
                                currentElement.getChoices().add(code);
                            }
                        }

                        // Other Terminology choices
                        for (String codeSystemKey : supportedCodeSystems.keySet()) {
                            code = getTerminologyCode(codeSystemKey, choices, row, colIds);
                            if (code != null) {
                                currentElement.getChoices().add(code);
                            }
                        }
                    }
                }
                else if (type == null || type.isEmpty()) {
                    currentGroup = label;
                }
                continue;
            }

            if (name.equals("NA")) {
                // TODO: Toaster message: create PlanDefinition
                continue;
            }

            if (currentElement == null || !currentElement.getName().equals(name)) {
                currentElement = new DictionaryElement(name);
                elementMap.put(name, currentElement);

                // Population based on the row:
                currentElement.setPage(page);
                currentElement.setGroup(currentGroup);
                currentElement.setLabel(label);
                currentElement.setInfoIcon(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "InfoIcon")));
                currentElement.setDue(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Due")));
                currentElement.setRelevance(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Relevance")));
                currentElement.setDescription(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Description")));
                currentElement.setNotes(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Notes")));
                currentElement.setType(type);
                currentElement.setCalculation(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Calculation")));
                currentElement.setConstraint(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Constraint")));
                currentElement.setRequired(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Required")));
                currentElement.setEditable(SpreadsheetHelper.getCellAsString(row, getColId(colIds, "Editable")));
                currentElement.setCode(getPrimaryCode(name, row, colIds));
                currentElement.setFhirType(getFhirType(row, colIds));
            }
        }
    }

    private boolean shouldCreateProfile(String type) {
        if (type == null) {
            return false;
        }

        switch (type) {
            case "Image":
            case "Note":
            case "QR Code":
            case "Text":
            case "Date":
            case "Checkbox":
            case "Integer":
            case "MC (select one)":
            case "MC (select multiple)":
                return true;
            default:
                return false;
        }
    }

    private boolean isMultipleChoiceElement(DictionaryElement element) {
        if (element.getType() == null) {
            return false;
        }

        switch (element.getType()) {
            case "MC (select multiple)":
                return true;
            default:
                return false;
        }
    }

    private void processElementMap() {
        for (DictionaryElement element : elementMap.values()) {
            if (shouldCreateProfile(element.getType())) {
                profiles.add(createProfile(element));
            }
        }
    }

    private String toId(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        return name.toLowerCase().replace("_", "-");
    }

    private boolean toBoolean(String value) {
        return "Yes".equals(value);
    }

    private String toFhirType(String type) {
        switch (type) {
            case "Image": return "Attachment";
            case "Note": return "Annotation";
            case "QR Code": return "Attachment"; // TODO: Consider specifying mime type as QR Code here?
            case "Text": return "markdown";
            case "Date": return "date";
            case "DateTime": return "dateTime";
            case "Time": return "time";
            case "Checkbox": return "boolean";
            case "Integer": return "integer";
            case "Decimal": return "decimal";
            case "Quantity": return "Quantity";
            case "MC (select one)":
            case "MC (select multiple)": return "CodeableConcept";
            default:
                throw new IllegalArgumentException(String.format("Unknown type code %s", type));
        }
    }

    private String toFhirObservationType(String type) {
        String fhirType = toFhirType(type);
        switch (fhirType) {
            case "markdown": return "string";
            case "date": return "dateTime";
            default: return fhirType;
        }
    }

    private StructureDefinition createProfile(DictionaryElement element) {
        String resourceType = element.getFhirType().getResourceType();
        String codePath = null;
        String choicesPath;
        switch (resourceType) {
            case "Observation":
                // For observations...
                codePath = "code";
                choicesPath = element.getFhirType().getResourcePath(); //"value[x]";
                break;
            case "Patient":
            case "Coverage":
            case "Encounter":
                choicesPath = element.getFhirType().getResourcePath();
                break;
            default:
                throw new IllegalArgumentException("Unrecognized baseType: " + resourceType.toString());
        }

        StructureDefinition sd = new StructureDefinition();
        sd.setId(toId(element.getName()));
        sd.setUrl(String.format("%s/StructureDefinition/%s", canonicalBase, sd.getId()));
        // TODO: version
        sd.setName(element.getName());
        sd.setTitle(element.getLabel());
        sd.setStatus(Enumerations.PublicationStatus.DRAFT);
        sd.setExperimental(false);
        // TODO: date
        // TODO: publisher
        // TODO: contact
        sd.setDescription(element.getDescription());
        // TODO: What to do with Notes?
        sd.setFhirVersion(Enumerations.FHIRVersion._4_0_0);
        sd.setKind(StructureDefinition.StructureDefinitionKind.RESOURCE);
        sd.setAbstract(false);
        // TODO: Support resources other than Observation
        sd.setType(resourceType);
        // TODO: Use baseDefinition to derive from less specialized profiles
        sd.setBaseDefinition(element.getFhirType().getBaseProfile());
        sd.setDerivation(StructureDefinition.TypeDerivationRule.CONSTRAINT);
        sd.setDifferential(new StructureDefinition.StructureDefinitionDifferentialComponent());

        List<ElementDefinition> elementDefinitions = new ArrayList<>();
        // Add root element
        ElementDefinition ed = new ElementDefinition();
        ed.setId(resourceType);
        ed.setPath(resourceType);
        ed.setMustSupport(false);
        elementDefinitions.add(ed);

        // TODO: status

        // TODO: category

        if (codePath != null && !codePath.isEmpty() && element.getCode() != null) {
            // code - Fixed to the value of the OpenMRS code for this DictionaryElement
            ed = new ElementDefinition();
            ed.setId(String.format("%s.%s", resourceType, codePath));
            ed.setPath(String.format("%s.%s", resourceType, codePath));
            ed.setMin(1);
            ed.setMax("1");
            ed.setMustSupport(true);
            ed.setFixed(element.getCode().toCodeableConcept());
            elementDefinitions.add(ed);
        }

        // TODO: subject

        // TODO: effective[x]

        // value
        ed = new ElementDefinition();
        ed.setId(String.format("%s.%s", resourceType, choicesPath));
        ed.setPath(String.format("%s.%s", resourceType, choicesPath));
        ed.setMin(toBoolean(element.getRequired()) ? 1 : 0);
        ed.setMax(isMultipleChoiceElement(element) ? "*" : "1");
        ElementDefinition.TypeRefComponent tr = new ElementDefinition.TypeRefComponent();
        tr.setCode(toFhirObservationType(element.getType()));
        ed.addType(tr);
        ed.setMustSupport(true);

        // binding and CodeSystem/ValueSet for MultipleChoice elements
        if (element.getChoices().size() > 0) {
            CodeSystem codeSystem = new CodeSystem();
            if (element.getChoicesForSystem(openMRSSystem).size() > 0) {
                codeSystem.setId(sd.getId() + "-codes");
                codeSystem.setUrl(String.format("%s/CodeSystem/%s", canonicalBase, codeSystem.getId()));
                // TODO: version
                codeSystem.setName(element.getName() + "_codes");
                codeSystem.setTitle(String.format("%s codes", element.getLabel()));
                codeSystem.setStatus(Enumerations.PublicationStatus.DRAFT);
                codeSystem.setExperimental(false);
                // TODO: date
                // TODO: publisher
                // TODO: contact
                codeSystem.setDescription(String.format("Codes representing possible values for the %s element", element.getLabel()));
                codeSystem.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
                codeSystem.setCaseSensitive(true);

                // collect all the OpenMRS choices to add to the codeSystem
                for (DictionaryCode code : element.getChoicesForSystem(openMRSSystem)) {
                    CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
                    concept.setCode(code.getCode());
                    concept.setDisplay(code.getLabel());
                    codeSystem.addConcept(concept);
                }

                codeSystems.add(codeSystem);
            }

            ValueSet valueSet = new ValueSet();
            valueSet.setId(sd.getId() + "-values");
            valueSet.setUrl(String.format("%s/ValueSet/%s", canonicalBase, valueSet.getId()));
            // TODO: version
            valueSet.setName(element.getName() + "_values");
            valueSet.setTitle(String.format("%s values", element.getLabel()));
            valueSet.setStatus(Enumerations.PublicationStatus.DRAFT);
            valueSet.setExperimental(false);
            // TODO: date
            // TODO: publisher
            // TODO: contact
            valueSet.setDescription(String.format("Codes representing possible values for the %s element", element.getLabel()));
            valueSet.setImmutable(true);
            ValueSet.ValueSetComposeComponent compose = new ValueSet.ValueSetComposeComponent();
            valueSet.setCompose(compose);

            // Group by Supported Terminology System
            for (String codeSystemKey : supportedCodeSystems.keySet()) {
                String codeSystemUrl = supportedCodeSystems.get(codeSystemKey);
                List<DictionaryCode> systemCodes = element.getChoicesForSystem(codeSystemUrl);

                if (systemCodes.size() > 0) {
                    ValueSet.ConceptSetComponent conceptSet = new ValueSet.ConceptSetComponent();
                    compose.addInclude(conceptSet);
                    conceptSet.setSystem(codeSystemUrl);

                    for (DictionaryCode code : systemCodes) {
                        ValueSet.ConceptReferenceComponent conceptReference = new ValueSet.ConceptReferenceComponent();
                        conceptReference.setCode(code.getCode());
                        conceptReference.setDisplay(code.getLabel());
                        conceptSet.addConcept(conceptReference);
                    }
                }
            }

            if (element.getChoicesForSystem(openMRSSystem).size() == element.getChoices().size()) {
                codeSystem.setValueSet(valueSet.getUrl());
            }

            valueSets.add(valueSet);

            ElementDefinition.ElementDefinitionBindingComponent binding = new ElementDefinition.ElementDefinitionBindingComponent();
            binding.setStrength(Enumerations.BindingStrength.REQUIRED);
            binding.setValueSet(valueSet.getUrl());
            ed.setBinding(binding);
        }
        elementDefinitions.add(ed);

        for (ElementDefinition elementDef : elementDefinitions) {
            sd.getDifferential().addElement(elementDef);
        }

        return sd;
    }

    public void writeResource(Resource resource) {
        String outputFilePath = getOutputPath() + "/" + resource.getResourceType().toString().toLowerCase() + "-" + resource.getId() + "." + encoding;
        try (FileOutputStream writer = new FileOutputStream(outputFilePath)) {
            writer.write(
                    encoding.equals("json")
                            ? FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(resource).getBytes()
                            : FhirContext.forR4().newXmlParser().setPrettyPrint(true).encodeResourceToString(resource).getBytes()
            );
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Error writing resource: " + resource.getId());
        }
    }

    public void writeProfiles() {
        for (StructureDefinition sd : profiles) {
            writeResource(sd);

            // Generate JSON fragment for inclusion in the IG:
            /*
                "StructureDefinition/<id>": {
                    "source": "structuredefinition/structuredefinition-<id>.json",
                    "defns": "StructureDefinition-<id>-definitions.html",
                    "base": "StructureDefinition-<id>.html"
                }
             */
            igJsonFragments.add(String.format("\t\t\"StructureDefinition/%s\": {\r\n\t\t\t\"source\": \"structuredefinition/structuredefinition-%s.json\",\r\n\t\t\t\"defns\": \"StructureDefinition-%s-definitions.html\",\r\n\t\t\t\"base\": \"StructureDefinition-%s.html\"\r\n\t\t}",
                    sd.getId(), sd.getId(), sd.getId(), sd.getId()));

            // Generate XML fragment for the IG resource:
            /*
                <resource>
                    <reference>
                        <reference value="StructureDefinition/<id>"/>
                    </reference>
                    <groupingId value="main"/>
                </resource>
             */
            igResourceFragments.add(String.format("\t\t\t<resource>\r\n\t\t\t\t<reference>\r\n\t\t\t\t\t<reference value=\"StructureDefinition/%s\"/>\r\n\t\t\t\t</reference>\r\n\t\t\t\t<groupingId value=\"main\"/>\r\n\t\t\t</resource>", sd.getId()));
        }
    }

    public void writeCodeSystems() {
        for (CodeSystem cs : codeSystems) {
            writeResource(cs);

            // Generate JSON fragment for inclusion in the IG:
            /*
                "CodeSystem/<id>": {
                    "source": "codesystem/codesystem-<id>.json",
                    "base": "CodeSystem-<id>.html"
                }
             */
            igJsonFragments.add(String.format("\t\t\"CodeSystem/%s\": {\r\n\t\t\t\"source\": \"codesystem/codesystem-%s.json\",\r\n\t\t\t\"base\": \"CodeSystem-%s.html\"\r\n\t\t}",
                    cs.getId(), cs.getId(), cs.getId()));

            // Generate XML fragment for the IG resource:
            /*
                <resource>
                    <reference>
                        <reference value="CodeSystem/<id>"/>
                    </reference>
                    <groupingId value="main"/>
                </resource>
             */
            igResourceFragments.add(String.format("\t\t\t<resource>\r\n\t\t\t\t<reference>\r\n\t\t\t\t\t<reference value=\"CodeSystem/%s\"/>\r\n\t\t\t\t</reference>\r\n\t\t\t\t<groupingId value=\"main\"/>\r\n\t\t\t</resource>", cs.getId()));
        }
    }

    public void writeValueSets() {
        for (ValueSet vs : valueSets) {
            writeResource(vs);

            // Generate JSON fragment for inclusion in the IG:
            /*
                "ValueSet/<id>": {
                    "source": "valueset/valueset-<id>.json",
                    "base": "ValueSet-<id>.html"
                }
             */
            igJsonFragments.add(String.format("\t\t\"ValueSet/%s\": {\r\n\t\t\t\"source\": \"valueset/valueset-%s.json\",\r\n\t\t\t\"base\": \"ValueSet-%s.html\"\r\n\t\t}",
                    vs.getId(), vs.getId(), vs.getId()));

            // Generate XML fragment for the IG resource:
            /*
                <resource>
                    <reference>
                        <reference value="ValueSet/<id>"/>
                    </reference>
                    <groupingId value="main"/>
                </resource>
             */
            igResourceFragments.add(String.format("\t\t\t<resource>\r\n\t\t\t\t<reference>\r\n\t\t\t\t\t<reference value=\"ValueSet/%s\"/>\r\n\t\t\t\t</reference>\r\n\t\t\t\t<groupingId value=\"main\"/>\r\n\t\t\t</resource>", vs.getId()));
        }
    }

    public void writeIgJsonFragments() {
        try (FileOutputStream writer = new FileOutputStream(getOutputPath() + "/ig.json")) {
            writer.write(String.format("{\r\n%s\r\n}", String.join(",\r\n", igJsonFragments)).getBytes());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Error writing ig.json fragment");
        }
    }

    public void writeIgResourceFragments() {
        try (FileOutputStream writer = new FileOutputStream(getOutputPath() + "/ig.xml")) {
            writer.write(String.format(String.join("\r\n", igResourceFragments)).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Error writing ig.xml fragment");
        }
    }
}
