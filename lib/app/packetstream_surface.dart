import 'package:flutter/widgets.dart';

/// Mounts exactly one surface at a time. This matters for Android platform
/// views: an Offstage WebView can still reserve a composited surface above the
/// Flutter dashboard.
class PacketStreamSurface extends StatelessWidget {
  const PacketStreamSurface({
    super.key,
    required this.signingIn,
    required this.login,
    required this.dashboard,
  });

  final bool signingIn;
  final Widget login;
  final Widget dashboard;

  @override
  Widget build(BuildContext context) => signingIn ? login : dashboard;
}
