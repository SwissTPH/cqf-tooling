package org.opencds.cqf.tooling.terminology.federation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.AdditionalRequestHeadersInterceptor;
import ca.uhn.fhir.rest.gclient.IOperation;
import ca.uhn.fhir.rest.gclient.IOperationUntyped;
import ca.uhn.fhir.rest.gclient.IOperationUntypedWithInputAndPartialOutput;
import org.hl7.fhir.CodeableConcept;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ConceptSubsumptionOutcome;
import org.opencds.cqf.tooling.utilities.CanonicalUtils;

public class FhirTerminologyClient implements TerminologyService {

    private IGenericClient client;
    public FhirTerminologyClient(IGenericClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client is required");
        }

        this.client = client;
    }

    private FhirContext context;
    private Endpoint endpoint;
    public FhirTerminologyClient(FhirContext context, Endpoint endpoint) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        this.context = context;

        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint is required");
        }
        this.endpoint = endpoint;

        // TODO: BasicAuthInterceptor...

        this.client = context.newRestfulGenericClient(endpoint.getAddress());
        if (endpoint.hasHeader()) {
            AdditionalRequestHeadersInterceptor interceptor = new AdditionalRequestHeadersInterceptor();
            for (StringType header : endpoint.getHeader()) {
                String[] headerValues = header.getValue().split(":");
                if (headerValues.length == 2) {
                    interceptor.addHeaderValue(headerValues[0], headerValues[1]);
                }
                // TODO: Log malformed headers in the endpoint
            }
            client.registerInterceptor(interceptor);
        }
    }

    private RuntimeException toException(OperationOutcome outcome) {
        // TODO: Improve outcome to exception processing
        if (outcome.hasIssue()) {
            return new RuntimeException(String.format("%s.%s", outcome.getIssueFirstRep().getCode(), outcome.getIssueFirstRep().getDetails()));
        }
        else {
            return new RuntimeException("Errors occurred but no details were returned");
        }
    }

    private boolean treatCanonicalTailAsLogicalId = false;
    public boolean getTreatCanonicalTailAsLogicalId() {
        return this.treatCanonicalTailAsLogicalId;
    }
    public FhirTerminologyClient setTreatCanonicalTailAsLogicalId(boolean treatCanonicalTailAsLogicalId) {
        this.treatCanonicalTailAsLogicalId = treatCanonicalTailAsLogicalId;
        return this;
    }

    private Object prepareExpand(String url) {
        String canonical = CanonicalUtils.stripVersion(url);
        String version = CanonicalUtils.getVersion(url);
        IOperationUntyped operation = null;
        IOperationUntypedWithInputAndPartialOutput<Parameters> operationWithInput = null;
        if (treatCanonicalTailAsLogicalId) {
            operation = this.client.operation()
                    .onInstance(String.format("ValueSet/%s", CanonicalUtils.getId(canonical)))
                    .named("expand");
            if (version != null) {
                operationWithInput = operation.withParameter(Parameters.class, "valueSetVersion", new StringType().setValue(version));
            }
        }
        else {
            operation = this.client.operation()
                    .onType(ValueSet.class)
                    .named("expand");
            operationWithInput = operation.withParameter(Parameters.class, "url", new StringType().setValue(canonical));
            if (version != null) {
                operationWithInput = operationWithInput.andParameter("valueSetVersion", new StringType().setValue(version));
            }
        }
        return operationWithInput != null ? operationWithInput : operation;
    }

    private ValueSet processResultAsValueSet(Object result) {
        if (result instanceof ValueSet) {
            return (ValueSet)result;
        }
        else if (result instanceof OperationOutcome) {
            throw toException((OperationOutcome)result);
        }
        else if (result == null) {
            throw new RuntimeException("No result returned when invoking expand");
        }
        else {
            throw new RuntimeException(String.format("Unexpected result type %s when invoking expand", result.getClass().getName()));
        }
    }

    @Override
    @SuppressWarnings("unchecked") // Probably shouldn't be doing this, but it tells me I have an unchecked cast, but it won't let me check the instance of the parameterized generic...
    public ValueSet expand(String url) {
        Object operationObject = prepareExpand(url);
        IOperationUntyped operation = operationObject instanceof IOperationUntyped ? (IOperationUntyped)operationObject : null;
        IOperationUntypedWithInputAndPartialOutput<Parameters> operationWithInput = operationObject instanceof IOperationUntypedWithInputAndPartialOutput
                ? (IOperationUntypedWithInputAndPartialOutput<Parameters>)operationObject : null;

        Object result = operationWithInput != null ? operationWithInput.execute() : operation.withNoParameters(Parameters.class).execute();
        return processResultAsValueSet(result);
    }

    @Override
    @SuppressWarnings("unchecked") // Probably shouldn't be doing this, but it tells me I have an unchecked cast, but it won't let me check the instance of the parameterized generic...
    public ValueSet expand(String url, Iterable<String> systemVersion) {
        Object operationObject = prepareExpand(url);
        IOperationUntyped operation = operationObject instanceof IOperationUntyped ? (IOperationUntyped)operationObject : null;
        IOperationUntypedWithInputAndPartialOutput<Parameters> operationWithInput = operationObject instanceof IOperationUntypedWithInputAndPartialOutput
                ? (IOperationUntypedWithInputAndPartialOutput<Parameters>)operationObject : null;
        if (systemVersion != null) {
            for (String sv : systemVersion) {
                if (operationWithInput == null) {
                    operationWithInput = operation.withParameter(Parameters.class, "system-version", new CanonicalType().setValue(sv));
                }
                else {
                    operationWithInput = operationWithInput.andParameter("system-version", new CanonicalType().setValue(sv));
                }
            }
        }

        Object result = operationWithInput != null ? operationWithInput.execute() : operation.withNoParameters(Parameters.class).execute();
        return processResultAsValueSet(result);
    }

    @Override
    public Parameters lookup(String code, String systemUrl) {
        throw new UnsupportedOperationException("lookup(code, systemUrl)");
    }

    @Override
    public Parameters lookup(Coding coding) {
        throw new UnsupportedOperationException("lookup(coding)");
    }

    @Override
    public Parameters validateCodeInValueSet(String url, String code, String systemUrl, String display) {
        throw new UnsupportedOperationException("validateCodeInValueSet(url, code, systemUrl, display)");
    }

    @Override
    public Parameters validateCodingInValueSet(String url, Coding code) {
        throw new UnsupportedOperationException("validateCodingInValueSet(url, code)");
    }

    @Override
    public Parameters validateCodeableConceptInValueSet(String url, CodeableConcept concept) {
        throw new UnsupportedOperationException("validateCodeableConceptInValueSet(url, concept)");
    }

    @Override
    public Parameters validateCodeInCodeSystem(String url, String code, String systemUrl, String display) {
        throw new UnsupportedOperationException("validateCodeInCodeSystem(url, code, systemUrl, display)");
    }

    @Override
    public Parameters validateCodingInCodeSystem(String url, Coding code) {
        throw new UnsupportedOperationException("validateCodingInCodeSystem(url, code)");
    }

    @Override
    public Parameters validateCodeableConceptInCodeSystem(String url, CodeableConcept concept) {
        throw new UnsupportedOperationException("validateCodeableConceptInCodeSystem(url, concept)");
    }

    @Override
    public ConceptSubsumptionOutcome subsumes(String codeA, String codeB, String systemUrl) {
        throw new UnsupportedOperationException("subsumes(codeA, codeB, systemUrl)");
    }

    @Override
    public ConceptSubsumptionOutcome subsumes(Coding codeA, Coding codeB) {
        throw new UnsupportedOperationException("subsumes(codeA, codeB)");
    }
}
