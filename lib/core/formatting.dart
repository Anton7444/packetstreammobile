String formatBytes(int bytes, [int precision = 4]) {
  if (bytes < 0) throw const FormatException('Invalid bandwidth');
  final adjusted = bytes.toDouble();
  if (adjusted < 1024) return '${adjusted.round()} Bytes';
  if (adjusted < 1048576) {
    return '${_formatPrecision(adjusted / 1024, precision)} KB';
  }
  if (adjusted < 1073741824) {
    return '${_formatPrecision(adjusted / 1048576, precision)} MB';
  }
  return '${_formatPrecision(adjusted / 1073741824, precision)} GB';
}

String _formatPrecision(double value, int precision) {
  final text = value.toStringAsPrecision(precision);
  if (text.contains('e') || text.contains('E')) {
    return value.toStringAsFixed(1);
  }
  return text;
}

String formatBalance(String value) {
  if (!RegExp(r'^\d+(\.\d+)?$').hasMatch(value)) {
    throw const FormatException('Invalid balance');
  }
  final parts = value.split('.');
  var fraction = parts.length == 2
      ? parts[1].replaceFirst(RegExp(r'0+$'), '')
      : '';
  fraction = fraction.padRight(2, '0');
  return '\$${parts[0]}.$fraction';
}
