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

package eu.europeana.api2.v2.web.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.response.FacetField;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.mongodb.Mongo;

import eu.europeana.api2.model.enums.ApiLimitException;
import eu.europeana.api2.model.json.ApiError;
import eu.europeana.api2.utils.JsonUtils;
import eu.europeana.api2.v2.model.LimitResponse;
import eu.europeana.api2.v2.model.json.SearchResults;
import eu.europeana.api2.v2.model.json.Suggestions;
import eu.europeana.api2.v2.model.json.view.ApiView;
import eu.europeana.api2.v2.model.json.view.BriefView;
import eu.europeana.api2.v2.model.xml.kml.KmlResponse;
import eu.europeana.api2.v2.model.xml.rss.Channel;
import eu.europeana.api2.v2.model.xml.rss.Item;
import eu.europeana.api2.v2.model.xml.rss.RssResponse;
import eu.europeana.api2.v2.utils.ControllerUtils;
import eu.europeana.api2.v2.utils.FacetParameterUtils;
import eu.europeana.api2.v2.utils.ModelUtils;
import eu.europeana.corelib.db.entity.enums.RecordType;
import eu.europeana.corelib.db.exception.DatabaseException;
import eu.europeana.corelib.db.exception.LimitReachedException;
import eu.europeana.corelib.db.service.ApiKeyService;
import eu.europeana.corelib.db.service.ApiLogService;
import eu.europeana.corelib.db.service.UserService;
import eu.europeana.corelib.definitions.db.entity.relational.ApiKey;
import eu.europeana.corelib.definitions.solr.Facet;
import eu.europeana.corelib.definitions.solr.beans.ApiBean;
import eu.europeana.corelib.definitions.solr.beans.BriefBean;
import eu.europeana.corelib.definitions.solr.beans.IdBean;
import eu.europeana.corelib.definitions.solr.model.Query;
import eu.europeana.corelib.logging.Log;
import eu.europeana.corelib.logging.Logger;
import eu.europeana.corelib.solr.exceptions.SolrTypeException;
import eu.europeana.corelib.solr.model.ResultSet;
import eu.europeana.corelib.solr.service.SearchService;
import eu.europeana.corelib.solr.utils.SolrUtils;
import eu.europeana.corelib.utils.StringArrayUtils;
import eu.europeana.corelib.utils.service.OptOutService;
import eu.europeana.corelib.web.model.rights.RightReusabilityCategorizer;
import eu.europeana.corelib.web.service.EuropeanaUrlService;
import eu.europeana.corelib.web.support.Configuration;
import eu.europeana.corelib.web.utils.NavigationUtils;
import eu.europeana.corelib.web.utils.RequestUtils;

/**
 * @author Willem-Jan Boogerd <www.eledge.net/contact>
 */
@Controller
public class SearchController {

	@Log
	private Logger log;

	@Resource(name = "corelib_db_mongo")
	private Mongo mongo;

	@Resource
	private SearchService searchService;

	@Resource
	private ApiKeyService apiService;

	@Resource
	private UserService userService;

	@Resource
	private ApiLogService apiLogService;

	@Resource
	private OptOutService optOutService;

	@Resource
	private Configuration configuration;

	@Resource
	private EuropeanaUrlService urlService;

	@Resource
	private ControllerUtils controllerUtils;

	final static public int FACET_LIMIT = 16;

	@RequestMapping(value = "/v2/search.json", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView searchJson(
			@RequestParam(value = "query", required = true) String queryString,
			@RequestParam(value = "qf", required = false) String[] refinements,
			@RequestParam(value = "reusability", required = false) String[] aReusability,
			@RequestParam(value = "profile", required = false, defaultValue = "standard") String profile,
			@RequestParam(value = "start", required = false, defaultValue = "1") int start,
			@RequestParam(value = "rows", required = false, defaultValue = "12") int rows,
			@RequestParam(value = "facet", required = false) String[] aFacet,
			@RequestParam(value = "wskey", required = false) String wskey,
			@RequestParam(value = "callback", required = false) String callback,
			HttpServletRequest request,
			HttpServletResponse response) {

		// workaround of a Spring issue
		// (https://jira.springsource.org/browse/SPR-7963)
		String[] _qf = (String[]) request.getParameterMap().get("qf");
		if (_qf != null && _qf.length != refinements.length) {
			refinements = _qf;
		}

		boolean isFacetsRequested = isFacetsRequested(profile);
		String[] reusability = StringArrayUtils.splitWebParameter(aReusability);
		String[] facets = StringArrayUtils.splitWebParameter(aFacet);
		boolean isDefaultFacetsRequested = isDefaultFacetsRequested(profile, facets);
		facets = limitFacets(facets, isDefaultFacetsRequested);

		boolean isDefaultOrReusabilityFacetRequested = isDefaultOrReusabilityFacetRequested(profile, facets);
		Map<String, Integer> facetLimits = null;
		Map<String, Integer> facetOffsets = null;
		if (isFacetsRequested) {
			Map<String,String[]> parameterMap = request.getParameterMap();
			facetLimits = FacetParameterUtils.getFacetParams("limit", aFacet, parameterMap, isDefaultFacetsRequested);
			facetOffsets = FacetParameterUtils.getFacetParams("offset", aFacet, parameterMap, isDefaultFacetsRequested);
		}

		response.setCharacterEncoding("UTF-8");
		rows = Math.min(rows, configuration.getApiRowLimit());

		Map<String, String> valueReplacements = new HashMap<String, String>();
		if (ArrayUtils.isNotEmpty(reusability)) {
			valueReplacements = RightReusabilityCategorizer.mapValueReplacements(reusability, true);

			refinements = (String[]) ArrayUtils.addAll(
					refinements,
					valueReplacements.keySet().toArray(new String[valueReplacements.keySet().size()])
			);
		}

		Query query = new Query(SolrUtils.rewriteQueryFields(queryString))
				.setApiQuery(true)
				.setRefinements(refinements)
				.setPageSize(rows)
				.setStart(start - 1)
				.setParameter("facet.mincount", "1")
				.setParameter("fl", "*,score")
				.setAllowSpellcheck(false)
				.setAllowFacets(false);

		if (ArrayUtils.isNotEmpty(facets)) {
			query.setFacets(facets);
			if (facetLimits != null && !facetLimits.isEmpty()) {
				for (Map.Entry<String, Integer> entry : facetLimits.entrySet()) {
					query.setParameter(entry.getKey(), String.valueOf(entry.getValue()));
				}
			}
			if (facetOffsets != null && !facetOffsets.isEmpty()) {
				for (Map.Entry<String, Integer> entry : facetOffsets.entrySet()) {
					query.setParameter(entry.getKey(), String.valueOf(entry.getValue()));
				}
			}
		}

		query.setValueReplacements(valueReplacements);

		// reusability facet settings
		if (isDefaultOrReusabilityFacetRequested) {
			query.setFacetQueries(RightReusabilityCategorizer.getQueryFacets());
		}

		if (StringUtils.containsIgnoreCase(profile, "portal") || StringUtils.containsIgnoreCase(profile, "spelling")) {
			query.setAllowSpellcheck(true);
		}

		if (isFacetsRequested) {
			query.setAllowFacets(true);
			if (!query.hasParameter("f.DATA_PROVIDER.facet.limit")
					&& (ArrayUtils.contains(facets, "DATA_PROVIDER") || ArrayUtils.contains(facets, "DEFAULT"))) {
				query.setParameter("f.DATA_PROVIDER.facet.limit", "3000");
			}
		}

		LimitResponse limitResponse = null;
		try {
			limitResponse = controllerUtils.checkLimit(wskey, request.getRequestURL().toString(),
					"search.json", RecordType.SEARCH, profile);
		} catch (ApiLimitException e) {
			response.setStatus(e.getHttpStatus());
			return JsonUtils.toJson(new ApiError(e), callback);
		}

		Class<? extends IdBean> clazz;
		if (StringUtils.containsIgnoreCase(profile, "minimal")) {
			clazz = BriefBean.class;
		} else {
			clazz = ApiBean.class;
		}

		try {
			SearchResults<? extends IdBean> result = createResults(wskey, profile, 
					query, clazz, limitResponse.getApiKey().getUser().getId());
			result.requestNumber = limitResponse.getRequestNumber();
			if (StringUtils.containsIgnoreCase(profile, "params")) {
				result.addParams(RequestUtils.getParameterMap(request), "wskey");
				result.addParam("profile", profile);
				result.addParam("start", start);
				result.addParam("rows", rows);
			}

			if (log.isInfoEnabled()) {
				log.info("got response " + result.items.size());
			}
			apiLogService.logApiRequest(wskey, query.getQuery(), RecordType.SEARCH, profile);
			response.addHeader("Access-Control-Allow-Origin", "*");
			response.addHeader("Access-Control-Allow-Methods", "POST");
			response.addHeader("Access-Control-Max-Age", "1000");
			return JsonUtils.toJson(result, callback);
		} catch (SolrTypeException e) {
			log.error(wskey + " [search.json] ", e);
			response.setStatus(500);
			return JsonUtils.toJson(new ApiError(wskey, "search.json", e.getMessage()), callback);
		} catch (Exception e) {
			log.error(wskey + " [search.json] " + e.getClass().getSimpleName(), e);
			response.setStatus(500);
			return JsonUtils.toJson(new ApiError(wskey, "search.json", e.getMessage()), callback);
		}
	}

	/**
	 * Limits the number of facets
	 * @param facets
	 *   The user entered facet names list
	 * @param isDefaultFacetsRequested
	 *   Flag if default facets should be returned
	 * @return
	 *   The limited set of facet names
	 */
	public static String[] limitFacets(String[] facets, boolean isDefaultFacetsRequested) {
		List<String> requestedFacets = Arrays.asList(facets);
		List<String> allowedFacets = new ArrayList<String>();

		int count = 0;
		if (isDefaultFacetsRequested && !requestedFacets.contains("DEFAULT")) {
			count = Facet.values().length;
		}

		int increment;
		for (String facet : requestedFacets) {
			increment = 1;
			if (StringUtils.equals(facet, "DEFAULT")) {
				increment = Facet.values().length;
			}
			if (count + increment <= FACET_LIMIT) {
				allowedFacets.add(facet);
				count += increment;
			} else {
				break;
			}
		}

		return allowedFacets.toArray(new String[allowedFacets.size()]);
	}

	@RequestMapping(value = "/v2/suggestions.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView suggestionsJson(@RequestParam(value = "query", required = true) String query,
			@RequestParam(value = "rows", required = false, defaultValue = "10") int count,
			@RequestParam(value = "phrases", required = false, defaultValue = "false") boolean phrases,
			@RequestParam(value = "callback", required = false) String callback) {
		if (log.isInfoEnabled()) {
			log.info("phrases: " + phrases);
		}
		Suggestions response = new Suggestions(null, "suggestions.json");
		try {
			response.items = searchService.suggestions(query, count);
			response.itemsCount = response.items.size();
		} catch (SolrTypeException e) {
			return JsonUtils.toJson(new ApiError(null, "suggestions.json", e.getMessage()), callback);
		}
		return JsonUtils.toJson(response, callback);
	}

	@SuppressWarnings("unchecked")
	private <T extends IdBean> SearchResults<T> createResults(
			String apiKey,
			String profile,
			Query query,
			Class<T> clazz,
			long uid)
					throws SolrTypeException {
		SearchResults<T> response = new SearchResults<T>(apiKey, "search.json");
		ResultSet<T> resultSet = searchService.search(clazz, query);
		response.totalResults = resultSet.getResultSize();
		response.itemsCount = resultSet.getResults().size();
		response.items = resultSet.getResults();

		List<T> beans = new ArrayList<T>();
		for (T b : resultSet.getResults()) {
			if (b instanceof ApiBean) {
				ApiBean bean = (ApiBean) b;
				ApiView view = new ApiView(bean, profile, apiKey, uid, optOutService.check(bean.getId()));
				beans.add((T) view);
				// in case profile = 'minimal'
			} else if (b instanceof BriefBean) {
				BriefBean bean = (BriefBean) b;
				BriefView view = new BriefView(bean, profile, apiKey, uid, optOutService.check(bean.getId()));
				beans.add((T) view);
			}
		}

		List<FacetField> facetFields = resultSet.getFacetFields();
		if (resultSet.getQueryFacets() != null) {
			List<FacetField> allQueryFacetsMap = SolrUtils.extractQueryFacets(resultSet.getQueryFacets());
			if (allQueryFacetsMap != null && !allQueryFacetsMap.isEmpty()) {
				facetFields.addAll(allQueryFacetsMap);
			}
		}

		if (log.isInfoEnabled()) {
			log.info("beans: " + beans.size());
		}
		response.items = beans;
		if (StringUtils.containsIgnoreCase(profile, "facets") || StringUtils.containsIgnoreCase(profile, "portal")) {
			response.facets = ModelUtils.conventFacetList(resultSet.getFacetFields());
		}
		if (StringUtils.containsIgnoreCase(profile, "breadcrumb") || StringUtils.containsIgnoreCase(profile, "portal")) {
			response.breadCrumbs = NavigationUtils.createBreadCrumbList(query);
		}
		if (StringUtils.containsIgnoreCase(profile, "spelling") || StringUtils.containsIgnoreCase(profile, "portal")) {
			response.spellcheck = ModelUtils.convertSpellCheck(resultSet.getSpellcheck());
		}
		// if (StringUtils.containsIgnoreCase(profile, "suggestions") ||
		// StringUtils.containsIgnoreCase(profile, "portal")) {
		// }
		return response;
	}

	@RequestMapping(value = "/v2/search.kml", produces = MediaType.APPLICATION_XML_VALUE)
	// @RequestMapping(value = "/v2/search.kml", produces =
	// "application/vnd.google-earth.kml+xml")
	public @ResponseBody
	KmlResponse searchKml(
			@RequestParam(value = "query", required = true) String queryString,
			@RequestParam(value = "qf", required = false) String[] refinements,
			@RequestParam(value = "start", required = false, defaultValue = "1") int start,
			@RequestParam(value = "wskey", required = true) String wskey,
			HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		// workaround of a Spring issue
		// (https://jira.springsource.org/browse/SPR-7963)
		String[] _qf = (String[]) request.getParameterMap().get("qf");
		if (_qf != null && _qf.length != refinements.length) {
			refinements = _qf;
		}

		try {
			ApiKey apiKey = apiService.findByID(wskey);
			apiService.checkReachedLimit(apiKey);
		} catch (DatabaseException e) {
			response.setStatus(401);
			throw new Exception(e);
		} catch (LimitReachedException e) {
			response.setStatus(429);
			throw new Exception(e);
		}
		KmlResponse kmlResponse = new KmlResponse();
		Query query = new Query(SolrUtils.rewriteQueryFields(queryString))
				.setRefinements(refinements)
				.setApiQuery(true)
				.setAllowSpellcheck(false)
				.setAllowFacets(false);
		query.setRefinements("pl_wgs84_pos_lat_long:[* TO *]");
		try {
			ResultSet<BriefBean> resultSet = searchService.search(BriefBean.class, query);
			kmlResponse.document.extendedData.totalResults.value = Long.toString(resultSet.getResultSize());
			kmlResponse.document.extendedData.startIndex.value = Integer.toString(start);
			kmlResponse.setItems(resultSet.getResults());
			apiLogService.logApiRequest(wskey, query.getQuery(), RecordType.SEARCH_KML, "kml");
		} catch (SolrTypeException e) {
			response.setStatus(429);
			throw new Exception(e);
		}
		return kmlResponse;
	}

	@RequestMapping(value = "/v2/opensearch.rss", produces = MediaType.APPLICATION_XML_VALUE)
	// , produces = "?rss?")
	public @ResponseBody
	RssResponse openSearchRss(@RequestParam(value = "searchTerms", required = true) String queryString,
			@RequestParam(value = "startIndex", required = false, defaultValue = "1") int start,
			@RequestParam(value = "count", required = false, defaultValue = "12") int count) {
		RssResponse rss = new RssResponse();
		Channel channel = rss.channel;
		channel.startIndex.value = start;
		channel.itemsPerPage.value = count;
		channel.query.searchTerms = queryString;
		channel.query.startPage = start;

		try {
			Query query = new Query(SolrUtils.rewriteQueryFields(queryString)).setApiQuery(true).setPageSize(count)
					.setStart(start - 1).setAllowFacets(false).setAllowSpellcheck(false);
			ResultSet<BriefBean> resultSet = searchService.search(BriefBean.class, query);
			channel.totalResults.value = resultSet.getResultSize();
			for (BriefBean bean : resultSet.getResults()) {
				Item item = new Item();
				item.guid = urlService.getPortalRecord(false, bean.getId()).toString();
				item.title = getTitle(bean);
				item.description = getDescription(bean);
				/*
				 * String enclosure = getThumbnail(bean); if (enclosure != null) { item.enclosure = new
				 * Enclosure(enclosure); }
				 */
				item.link = item.guid;
				channel.items.add(item);
			}
		} catch (SolrTypeException e) {
			channel.totalResults.value = 0;
			Item item = new Item();
			item.title = "Error";
			item.description = e.getMessage();
			channel.items.add(item);
		}
		return rss;
	}

	private String getTitle(BriefBean bean) {
		if (!ArrayUtils.isEmpty(bean.getTitle())) {
			for (String title : bean.getTitle()) {
				if (!StringUtils.isBlank(title)) {
					return title;
				}
			}
		}
		return bean.getDataProvider()[0] + " " + bean.getId();
	}

	private String getDescription(BriefBean bean) {
		StringBuilder sb = new StringBuilder();
		if (bean.getDcCreator() != null && bean.getDcCreator().length > 0
				&& StringUtils.isNotBlank(bean.getDcCreator()[0])) {
			sb.append(bean.getDcCreator()[0]);
		}
		if (bean.getYear() != null && bean.getYear().length > 0) {
			if (sb.length() > 0) {
				sb.append("; ");
			}
			sb.append(StringUtils.join(bean.getYear(), ", "));
		}
		if (!ArrayUtils.isEmpty(bean.getProvider())) {
			if (sb.length() > 0) {
				sb.append("; ");
			}
			sb.append(StringUtils.join(bean.getProvider(), ", "));
		}
		return sb.toString();
	}

	private boolean isFacetsRequested(String profile) {
		if (StringUtils.containsIgnoreCase(profile, "portal") || StringUtils.containsIgnoreCase(profile, "facets")) {
			return true;
		}
		return false;
	}

	private boolean isDefaultFacetsRequested(String profile, String[] facets) {
		if (StringUtils.containsIgnoreCase(profile, "portal") || 
			(StringUtils.containsIgnoreCase(profile, "facets") 
				&& (    ArrayUtils.isEmpty(facets)
					||  ArrayUtils.contains(facets, "DEFAULT")
			))) {
			return true;
		}
		return false;
	}

	private boolean isDefaultOrReusabilityFacetRequested(String profile, String[] facets) {
		if (StringUtils.containsIgnoreCase(profile, "portal") || 
			(StringUtils.containsIgnoreCase(profile, "facets") 
				&& (
								ArrayUtils.isEmpty(facets) 
							||  ArrayUtils.contains(facets, "DEFAULT")
							||  ArrayUtils.contains(facets, "REUSABILITY")
			))) {
			return true;
		}
		return false;
	}


}
