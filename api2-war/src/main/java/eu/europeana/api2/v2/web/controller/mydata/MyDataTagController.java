/*
 * Copyright 2007-2013 The Europeana Foundation
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

package eu.europeana.api2.v2.web.controller.mydata;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import eu.europeana.api2.utils.JsonUtils;
import eu.europeana.api2.v2.model.json.UserResults;
import eu.europeana.api2.v2.model.json.user.Tag;
import eu.europeana.api2.v2.web.controller.abstracts.AbstractUserController;
import eu.europeana.corelib.db.entity.relational.custom.TagCloudItem;
import eu.europeana.corelib.db.exception.DatabaseException;
import eu.europeana.corelib.definitions.db.entity.relational.ApiKey;
import eu.europeana.corelib.definitions.db.entity.relational.SocialTag;
import eu.europeana.corelib.definitions.db.entity.relational.User;

/**
 * @author Willem-Jan Boogerd <www.eledge.net/contact>
 */
@Controller
public class MyDataTagController extends AbstractUserController {

	@RequestMapping(value = "/v2/mydata/tag.json", params = "!action", produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView defaultAction(
			@RequestParam(value = "wskey", required = true) String wskey,
			@RequestParam(value = "username", required = true) String username,
			@RequestParam(value = "europeanaid", required = false) String europeanaId,
			@RequestParam(value = "tag", required = false) String tag,
			@RequestParam(value = "callback", required = false) String callback) {
		return list(wskey, username, europeanaId, tag, callback);
	}

	@RequestMapping(value = "/v2/mydata/tag.json", params = "action=LIST", produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView list(
			@RequestParam(value = "wskey", required = true) String wskey,
			@RequestParam(value = "username", required = true) String username,
			@RequestParam(value = "europeanaid", required = false) String europeanaId,
			@RequestParam(value = "tag", required = false) String tagFilter,
			@RequestParam(value = "callback", required = false) String callback) {
		UserResults<Tag> response = new UserResults<Tag>(wskey, "/v2/mydata/tag.json");
		try {
			ApiKey apiKey = apiKeyService.findByID(wskey);
			if ((apiKey != null) && StringUtils.equalsIgnoreCase(username, apiKey.getUser().getUserName())) {
				User user = apiKey.getUser();
				response.items = new ArrayList<Tag>();
				response.username = user.getUserName();
				List<SocialTag> tags;
				if (StringUtils.isNotBlank(tagFilter)) {
					tags = userService.findSocialTagsByTag(user.getId(), tagFilter);
				} else if (StringUtils.isNotBlank(europeanaId)) {
					tags = userService.findSocialTagsByEuropeanaId(user.getId(), europeanaId);
				} else {
					tags = new ArrayList<SocialTag>(user.getSocialTags());
				}
				response.itemsCount = Long.valueOf(tags.size());
				for (SocialTag item : tags) {
					Tag tag = new Tag();
					copyUserObjectData(response.apikey, tag, item);
					tag.tag = item.getTag();
					response.items.add(tag);
				}
				return JsonUtils.toJson(response, callback);
			} else {
				response.success = false;
				response.error = "Invalid credentials";
			}
		} catch (DatabaseException e) {
			response.success = false;
			response.error = e.getMessage();
		}
		return JsonUtils.toJson(response, callback);
	}
	

	@RequestMapping(value = "/v2/mydata/tag.json", params = "action=TAGCLOUD", produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView listDistinct(
			@RequestParam(value = "wskey", required = true) String wskey,
			@RequestParam(value = "username", required = true) String username,
			@RequestParam(value = "callback", required = false) String callback) {
		UserResults<TagCloudItem> response = new UserResults<TagCloudItem>(wskey, "/v2/mydata/tag.json?action=TAGCLOUD");
		try {
			ApiKey apiKey = apiKeyService.findByID(wskey);
			if ((apiKey != null) && StringUtils.equalsIgnoreCase(username, apiKey.getUser().getUserName())) {
				User user = apiKey.getUser();
				try {
					response.items = userService.createSocialTagCloud(user.getId());
					response.itemsCount = Long.valueOf(response.items.size());
					response.success = true;
				} catch (DatabaseException e) {
					response.success = false;
					response.error = e.getMessage();
				}
				response.username = user.getUserName();
			} else {
				response.success = false;
				response.error = "Invalid credentials";
			}
		} catch (DatabaseException e) {
			response.success = false;
			response.error = e.getMessage();
		}
		return JsonUtils.toJson(response, callback);
	}
	
	
}