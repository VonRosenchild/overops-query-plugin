package org.overops.plugins.jenkins.query;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.overops.plugins.common.api.util.ApiEventUtil;
import org.overops.plugins.common.util.RegressionUtil;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.result.event.EventResult;

public class RegressionReportBuilder {

	public static class RegressionReport {

		private final List<OOReportEvent> newIssues;
		private final List<OOReportRegressedEvent> regressions;
		private final List<OOReportEvent> allIssues;
		private final boolean unstable;

		RegressionReport(List<OOReportEvent> newIssues, List<OOReportRegressedEvent> regressions, boolean unstable) {
			this.newIssues = newIssues;
			this.regressions = regressions;
			this.allIssues = new ArrayList<>();
			
			allIssues.addAll(newIssues);
			allIssues.addAll(regressions);
			
			this.unstable = unstable;
		}
		
		public List<OOReportEvent> getAllIssues() {
			return allIssues;
		}

		public List<OOReportEvent> getNewIssues() {
			return newIssues;
		}

		public List<OOReportRegressedEvent> getRegressions() {
			return regressions;
		}

		public boolean getUnstable() {
			return unstable;
		}
	}
	
	public static String getArcLink(ApiClient apiClient, String serviceId, String eventId, int timespan) {

		String result = ApiEventUtil.getEventRecentLink(apiClient, serviceId, eventId, timespan);

		return result;
	}     

	public static RegressionReport execute(ApiClient apiClient, String serviceId, String viewId, int activeTimespan,
			int baselineTimespan, String criticalExceptionTypes, int minVolumeThreshold, double minErrorRateThreshold,
			double regressionDelta, double criticalRegressionDelta, PrintStream output) {

		Collection<String> criticalExceptionTypeList;

		if (criticalExceptionTypes != null) {
			criticalExceptionTypeList = Arrays.asList(criticalExceptionTypes.split(","));
		} else {
			criticalExceptionTypeList = Collections.emptyList();
		}

		RegressionUtil.RateRegression rateRegression = RegressionUtil.calculateRateRegressions(apiClient, serviceId,
				viewId, activeTimespan, baselineTimespan, minVolumeThreshold, minErrorRateThreshold,
				regressionDelta, criticalRegressionDelta, criticalExceptionTypeList, output);
		
		List<OOReportEvent> newIssues = getAllNewEvents(apiClient, serviceId, baselineTimespan, rateRegression);
		List<OOReportRegressedEvent> regressions = getAllRegressions(apiClient, serviceId, baselineTimespan, rateRegression);

		boolean unstable = (rateRegression.getCriticalNewEvents().size() > 0)
				|| (rateRegression.getExceededNewEvents().size() > 0)
				|| (rateRegression.getCriticalRegressions().size() > 0);
		
		return new RegressionReport(newIssues, regressions, unstable);
	}

	private static List<OOReportEvent> getReportSevereEvents(ApiClient apiClient, String serviceId, int timespan,
			Collection<EventResult> events, String type) {

		List<OOReportEvent> result = new ArrayList<>();

		for (EventResult event : events) {

			String arcLink = getArcLink(apiClient, serviceId, event.id, timespan);
			OOReportEvent reportEvent = new OOReportEvent(event, type, arcLink);

			result.add(reportEvent);
		}

		return result;
	}
	
	private static List<OOReportEvent> getReportNewEvents(ApiClient apiClient, String serviceId,
			int timespan, RegressionUtil.RateRegression rateRegression) {
		
		List<OOReportEvent> result = new ArrayList<>();
		
		for (EventResult event : rateRegression.getAllNewEvents().values()) {
	
			if (rateRegression.getCriticalNewEvents().containsKey(event.id)) {
				continue;
			}
			
			if (rateRegression.getExceededNewEvents().containsKey(event.id)) {
				continue;
			}
			
			String arcLink = getArcLink(apiClient, serviceId, event.id, timespan);
	
			OOReportEvent newEvent = new OOReportEvent(event, OOReportEvent.NEW_ISSUE, arcLink);
	
			result.add(newEvent);
		}
		
		return result;
	}

	
	private static List<OOReportEvent> getAllNewEvents(ApiClient apiClient, String serviceId,
			int baselineTimespan, RegressionUtil.RateRegression rateRegression) {
		
		List<OOReportEvent> result = new ArrayList<>();
		
		result.addAll(getReportSevereEvents(apiClient, serviceId, baselineTimespan,
			rateRegression.getCriticalNewEvents().values(), OOReportEvent.SEVERE_NEW));
		
		result.addAll(getReportSevereEvents(apiClient, serviceId, baselineTimespan,
			rateRegression.getExceededNewEvents().values(), OOReportEvent.SEVERE_NEW));
		
		result.addAll(getReportNewEvents(apiClient, serviceId, baselineTimespan, rateRegression));
		
		return result;
		
	}
	private static List<OOReportRegressedEvent> getAllRegressions(ApiClient apiClient, String serviceId,
			int timespan, RegressionUtil.RateRegression rateRegression) {

		List<OOReportRegressedEvent> result = new ArrayList<>();

		for (RegressionUtil.RegressionPair pair : rateRegression.getCriticalRegressions().values()) {

			String arcLink = getArcLink(apiClient, serviceId, pair.getActiveEvent().id, timespan);

			OOReportRegressedEvent regressedEvent = new OOReportRegressedEvent(pair.getActiveEvent(),
					pair.getBaseEvent(), OOReportEvent.SEVERE_REGRESSION, arcLink);

			result.add(regressedEvent);
		}
		
		for (RegressionUtil.RegressionPair pair : rateRegression.getAllRegressions().values()) {

			if (rateRegression.getCriticalRegressions().containsKey(pair.getActiveEvent().id)) {
				continue;
			}
			
			String arcLink = getArcLink(apiClient, serviceId, pair.getActiveEvent().id, timespan);

			OOReportRegressedEvent regressedEvent = new OOReportRegressedEvent(pair.getActiveEvent(),
					pair.getBaseEvent(), OOReportEvent.REGRESSION, arcLink);

			result.add(regressedEvent);
		}

		return result;
	}
}