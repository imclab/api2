/*
 * Copyright 2007-2012 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved 
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *  
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under 
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of 
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under 
 *  the Licence.
 */

package eu.europeana.api2.v2.model.xml.rss;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import eu.europeana.corelib.web.service.EuropeanaUrlService;

/**
 * @author Willem-Jan Boogerd <www.eledge.net/contact>
 */
@XmlRootElement(name = "rss")
public class RssResponse {

	public static final String NS_ATOM = "http://www.w3.org/2005/Atom";
	public static final String NS_OPENSEARCH = "http://a9.com/-/spec/opensearch/1.1/";
	public static final String NS_DC = "http://purl.org/dc/elements/1.1/";
	public static final String NS_DCTERM = "http://purl.org/dc/terms/";
	public static final String NS_EUROPEANA = EuropeanaUrlService.URL_EUROPEANA;
	public static final String NS_ENRICHMENT = EuropeanaUrlService.URL_EUROPEANA+"/schemas/ese/enrichment/";

	@XmlAttribute
	final String version = "2.0";

	@XmlElement(name = "channel")
	public Channel channel = new Channel();
}
