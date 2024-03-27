/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.bfh.ti.i4mi.mag.mhd.iti66;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Body;
import org.openehealth.ipf.commons.ihe.fhir.iti66_v401.Iti66SearchParameters;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.requests.QueryRegistry;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindSubmissionSetsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetSubmissionSetAndContentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.Query;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.QueryReturnType;

import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ch.bfh.ti.i4mi.mag.BaseRequestConverter;
import org.springframework.stereotype.Component;

/**
 * ITI-66 to ITI-18 request converter
 * @author alexander kreutz
 *
 */
@Component
public class Iti66RequestConverter extends BaseRequestConverter {

	 /**
	  * convert ITI-66 request to ITI-18 request
	  * @param searchParameter
	  * @return
	  */
	 public QueryRegistry searchParameterIti66ToFindSubmissionSetsQuery(@Body Iti66SearchParameters searchParameter) {
	      
         boolean getLeafClass = true;
       
         Query searchQuery = null;
         
         if (searchParameter.getIdentifier() != null || searchParameter.get_id() != null) {

			 final GetSubmissionSetAndContentsQuery query = getGetSubmissionSetAndContentsQuery(searchParameter);
			 searchQuery = query;

         } else {

	         final FindSubmissionSetsQuery query = new FindSubmissionSetsQuery();
	         
	         if (searchParameter.getCode() != null && ! searchParameter.getCode().getValue().equals("submissionset")) {
	        	 throw new InvalidRequestException("Only search for submissionsets supported.");
	         }
	         
	         
	         // patient or patient.identifier -> $XDSSubmissionSetPatientId
	         TokenParam tokenIdentifier = searchParameter.getPatientIdentifier();
	         if (tokenIdentifier != null) {
	         	String system = getScheme(tokenIdentifier.getSystem());
	         	if (system==null) throw new InvalidRequestException("Missing OID for patient");
	         	/*if (system.startsWith("urn:oid:")) {
	                 system = system.substring(8);
	             }*/
	         	
	              query.setPatientId(new Identifiable(tokenIdentifier.getValue(), new AssigningAuthority(system)));
	         } 
	         ReferenceParam patientRef =  searchParameter.getPatientReference();
	         if (patientRef != null) {
	        	 Identifiable id = transformReference(patientRef.getValue());
	        	 query.setPatientId(id);
	         }
	        
	         // created Note 1 -> $XDSSubmissionSetSubmissionTimeFrom
	         // created Note 2 -> $XDSSubmissionSetSubmissionTimeTo 
	         DateRangeParam createdRange = searchParameter.getDate();
	         if (createdRange != null) {
		            DateParam creationTimeFrom = createdRange.getLowerBound();
		            DateParam creationTimeTo = createdRange.getUpperBound();
		            query.getSubmissionTime().setFrom(timestampFromDateParam(creationTimeFrom));
		            query.getSubmissionTime().setTo(timestampFromDateParam(creationTimeTo));
	         }            
	         
	         // TODO author.given / author.family -> $XDSSubmissionSetAuthorPerson
	         StringParam authorGivenName = searchParameter.getSourceGiven();
	         StringParam authorFamilyName = searchParameter.getSourceFamily();
	         if (authorGivenName != null || authorFamilyName != null) {
		            String author = (authorGivenName != null ? authorGivenName.getValue() : "%")+" "+(authorFamilyName != null ? authorFamilyName.getValue() : "%");	            
		            query.setAuthorPerson(author);
	         }
	                                 
	         // type -> $XDSSubmissionSetContentType
	         TokenOrListParam types = searchParameter.getDesignationType();
	         query.setContentTypeCodes(codesFromTokens(types));
	         
	         
	         // source -> $XDSSubmissionSetSourceId 
	         TokenOrListParam sources = searchParameter.getSourceId();
	         query.setSourceIds(urisFromTokens(sources));
	         
	         // status -> $XDSSubmissionSetStatus 
	         TokenOrListParam status = searchParameter.getStatus();
	         if (status != null) {
		            List<AvailabilityStatus> availabilites = new ArrayList<AvailabilityStatus>();
		            for (TokenParam statusToken : status.getValuesAsQueryTokens()) {
		            	String tokenValue = statusToken.getValue();
		            	if (tokenValue.equals("current")) availabilites.add(AvailabilityStatus.APPROVED);
		            	else if (tokenValue.equals("superseded")) availabilites.add(AvailabilityStatus.DEPRECATED);
		            }            
		            query.setStatus(availabilites);
	         }       
	         searchQuery = query;
         }

         final QueryRegistry queryRegistry = new QueryRegistry(searchQuery);
         
                  
         queryRegistry.setReturnType((getLeafClass) ? QueryReturnType.LEAF_CLASS : QueryReturnType.OBJECT_REF);

         return queryRegistry;
     
 }

	private static GetSubmissionSetAndContentsQuery getGetSubmissionSetAndContentsQuery(Iti66SearchParameters searchParameter) {

		final GetSubmissionSetAndContentsQuery query = new GetSubmissionSetAndContentsQuery();
		if (searchParameter.getIdentifier() != null) {
			String val = searchParameter.getIdentifier().getValue();
			if (val.startsWith("urn:oid:")) {
				query.setUniqueId(val.substring("urn:oid:".length()));
			} else if (val.startsWith("urn:uuid:")) {
				query.setUuid(val.substring("urn:uuid:".length()));
			}
		} else {
			query.setUuid(searchParameter.get_id().getValue());
		}
		return query;
	}
}
