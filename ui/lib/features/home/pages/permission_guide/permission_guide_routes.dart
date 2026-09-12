class PermissionGuideRoutes {
  PermissionGuideRoutes._();

  static String index({String? brand}) {
    final queryParameters = <String, String>{};
    final trimmedBrand = brand?.trim();
    if (trimmedBrand != null && trimmedBrand.isNotEmpty) {
      queryParameters['brand'] = trimmedBrand;
    }
    final query = Uri(queryParameters: queryParameters).query;
    if (query.isEmpty) {
      return '/home/permission_guide';
    }
    return '/home/permission_guide?$query';
  }

  static String detail({
    required String brand,
    required String type,
  }) {
    final query = Uri(
      queryParameters: {
        'brand': brand,
        'type': type,
      },
    ).query;
    return '/home/permission_guide/detail?$query';
  }
}
