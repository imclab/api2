package eu.europeana.api2.v2.utils;

import javax.annotation.Resource;

import eu.europeana.api2.model.enums.ApiLimitException;
import eu.europeana.api2.v2.model.LimitResponse;
import eu.europeana.corelib.db.entity.enums.RecordType;
import eu.europeana.corelib.db.exception.DatabaseException;
import eu.europeana.corelib.db.exception.LimitReachedException;
import eu.europeana.corelib.db.service.ApiKeyService;
import eu.europeana.corelib.db.service.ApiLogService;
import eu.europeana.corelib.definitions.db.entity.relational.ApiKey;

public class ControllerUtils {

	@Resource
	private ApiKeyService apiService;

	@Resource
	private ApiLogService apiLogService;

	public LimitResponse checkLimit(String wskey, String url, String apiCall, RecordType recordType, String profile) 
			throws ApiLimitException {
		ApiKey apiKey = null;
		long requestNumber = 0;
		try {
			apiKey = apiService.findByID(wskey);
			if (apiKey == null) {
				throw new ApiLimitException(wskey, apiCall, "Unregistered user", 0, 401);
			}
			apiKey.getUsageLimit();
			requestNumber = apiService.checkReachedLimit(apiKey);
			apiLogService.logApiRequest(wskey, url, recordType, profile);
		} catch (DatabaseException e) {
			apiLogService.logApiRequest(wskey, url, recordType, profile);
			throw new ApiLimitException(wskey, apiCall, e.getMessage(), requestNumber, 401);
		} catch (LimitReachedException e) {
			apiLogService.logApiRequest(wskey, url, RecordType.LIMIT, recordType.toString());
			throw new ApiLimitException(wskey, apiCall, e.getMessage(), requestNumber, 429);
		}
		return new LimitResponse(apiKey, requestNumber);
	}
}
