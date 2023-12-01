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

package ch.bfh.ti.i4mi.mag.pmir;

import bsh.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Reference;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ch.bfh.ti.i4mi.mag.Config;
import ch.bfh.ti.i4mi.mag.MobileAccessGateway;
import ch.bfh.ti.i4mi.mag.mhd.SchemeMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * create a patient reference using the mobile health gateway base address 
 * @author alexander kreutz
 *
 */
@Slf4j
public class PatientReferenceCreator {

	@Autowired
	private Config config;
	
	@Autowired
	private SchemeMapper schemeMapper;
	
	
	/**
	 * create patient reference from identifiable authority and value
	 * @param system
	 * @param value
	 * @return
	 */
	public Reference createPatientReference(String system, String value) {
		Reference result = new Reference();
		//result.setReference(config.getUriPatientEndpoint()+"?identifier=urn:oid:"+system+"|"+value);		
		result.setReference(config.getUriPatientEndpoint()+"/"+createPatientId(system,value));
		return result;
	}
	
	public String createPatientId(String system, String value) {
		return system+"-"+value;
	}
	
	public Identifiable resolvePatientReference(String reference) {
		if (reference.contains("?")) {
			reference = reference.substring(reference.indexOf("?"));
			String[] fragments = StringUtils.split(reference, '&');
			for (String fragment : fragments) {
				if (fragment.startsWith("identifier=")) {
					String identifier = fragment.substring("identifier=".length());
					String[] fragments1 = StringUtils.split(identifier, '|');
					if (fragments1.length == 2) {
						if (fragments1[0].startsWith("urn:oid:")) {
							fragments1[0] = fragments1[0].substring("urn:oid:".length());
						}
						return new Identifiable(fragments1[1], new AssigningAuthority(fragments1[0]));
					}
				}
			}
		}
		return null;
	}
}
