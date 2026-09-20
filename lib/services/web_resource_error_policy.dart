enum DashboardHttpFailure { sessionExpired, unavailable }

DashboardHttpFailure? dashboardHttpError(
  int? statusCode, {
  required String? requestUrl,
  required String? currentUrl,
}) {
  if (requestUrl == null || currentUrl == null || requestUrl != currentUrl) {
    return null;
  }
  return statusCode == 401
      ? DashboardHttpFailure.sessionExpired
      : DashboardHttpFailure.unavailable;
}
