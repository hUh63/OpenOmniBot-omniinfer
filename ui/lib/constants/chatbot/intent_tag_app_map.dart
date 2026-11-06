/// 意图标签到应用名称的映射
///
/// 用于当用户未明确指定应用时，根据意图标签扩充可用应用列表
const Map<String, List<String>> intentTagToAppMap = {
  'drink_order': ['美团', '饿了么', '朴朴', '淘宝闪购'],
  'ecommerce': ['淘宝', '拼多多', '京东', '闲鱼'],
  'ride_hailing': ['滴滴出行'],
  'navigation': ['高德地图', '百度地图'],
};
