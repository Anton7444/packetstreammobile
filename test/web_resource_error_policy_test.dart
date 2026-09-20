import 'package:flutter_test/flutter_test.dart';
import 'package:packetstream_mobile/services/web_resource_error_policy.dart';

void main() {
  test('ignores HTTP errors from subresources', () {
    expect(
      dashboardHttpError(
        404,
        requestUrl: 'https://app.packetstream.io/favicon.ico',
        currentUrl: 'https://app.packetstream.io/dashboard',
      ),
      isNull,
    );
  });

  test('maps a main-frame unauthorized response to session expiry', () {
    expect(
      dashboardHttpError(
        401,
        requestUrl: 'https://app.packetstream.io/dashboard',
        currentUrl: 'https://app.packetstream.io/dashboard',
      ),
      DashboardHttpFailure.sessionExpired,
    );
  });

  test('maps other main-frame HTTP errors to dashboard failure', () {
    expect(
      dashboardHttpError(
        500,
        requestUrl: 'https://app.packetstream.io/dashboard',
        currentUrl: 'https://app.packetstream.io/dashboard',
      ),
      DashboardHttpFailure.unavailable,
    );
  });
}
