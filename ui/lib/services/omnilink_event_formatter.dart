/// Formats the safe timestamp carried by an OmniLink notification summary.
///
/// The notification title/body never crosses the OmniLink Agent boundary. The
/// timestamp is safe metadata and helps a user distinguish a new event from
/// an old one when several devices are being monitored.
String formatOmniLinkNotificationTime(dynamic raw) {
  if (raw is! num || !raw.isFinite) return '';
  try {
    final value = raw.toInt();
    final dateTime = DateTime.fromMillisecondsSinceEpoch(value).toLocal();
    final hour = dateTime.hour.toString().padLeft(2, '0');
    final minute = dateTime.minute.toString().padLeft(2, '0');
    return '$hour:$minute';
  } catch (_) {
    return '';
  }
}
