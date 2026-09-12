/// 模型厂商识别目录。
///
/// 参考 DEEIX-Chat 的 model-identity 方案：每个厂商定义「别名（精确/前缀匹配）+
/// 正则模式（模型 ID 文本匹配）」，识别优先级为：
/// 模型 ID 正则匹配 → ownedBy 字段 → providerId 字段。
/// 目录顺序即分组展示顺序；openrouter / copilot 等聚合方排在最后，
/// 避免抢占具体厂商的匹配。
class ModelVendorInfo {
  const ModelVendorInfo({
    required this.key,
    required this.label,
    this.labelZh,
    this.iconIsMonochrome = false,
    required this.aliases,
    required this.patterns,
  });

  /// 厂商唯一标识，同时作为分组 key 与图标文件名。
  final String key;

  /// 分组标题展示名（英文/品牌名）。
  final String label;

  /// 中文展示名；为空时使用 [label]。
  final String? labelZh;

  /// 图标是否为单色（单色图标渲染时按主题文字色着色）。
  final bool iconIsMonochrome;

  /// 精确/前缀匹配的别名（用于 ownedBy、providerId 等声明字段）。
  final List<String> aliases;

  /// 模型 ID 文本匹配的正则。
  final List<RegExp> patterns;

  String get iconAsset => 'assets/provider_icons/$key.svg';

  /// 按语言返回展示名。
  String labelForLanguage(String languageCode) {
    if (languageCode == 'zh' && labelZh != null && labelZh!.isNotEmpty) {
      return labelZh!;
    }
    return label;
  }
}

RegExp _re(String source) => RegExp(source, caseSensitive: false);

class ModelVendorCatalog {
  ModelVendorCatalog._();

  /// 未识别厂商的分组 key。
  static const String otherGroupKey = 'other';

  static final List<ModelVendorInfo> vendors = [
    ModelVendorInfo(
      key: 'openai',
      label: 'OpenAI',
      iconIsMonochrome: true,
      aliases: const ['openai', 'chatgpt'],
      patterns: [
        _re(r'\bchatgpt\b'),
        _re(r'\bgpt(?:-[a-z0-9.]+)?\b'),
        _re(r'\bo[134]\b'),
        _re(r'\bcodex\b'),
        _re(r'\bdall-e\b'),
        _re(r'\bsora\b'),
        _re(r'\bwhisper\b'),
        _re(r'\bdavinci\b'),
      ],
    ),
    ModelVendorInfo(
      key: 'anthropic',
      label: 'Anthropic',
      iconIsMonochrome: true,
      aliases: const ['anthropic', 'claude'],
      patterns: [_re(r'\bclaude\b')],
    ),
    ModelVendorInfo(
      key: 'google',
      label: 'Google',
      aliases: const ['google', 'gemini', 'gemma', 'nano-banana'],
      patterns: [
        _re(r'\bnano-banana\b'),
        _re(r'\bgemini\b'),
        _re(r'\bgemma\b'),
        _re(r'\bimagen\b'),
        _re(r'\bveo\b'),
        _re(r'\blearnlm\b'),
      ],
    ),
    ModelVendorInfo(
      key: 'meta',
      label: 'Meta',
      aliases: const ['meta', 'llama', 'meta-llama'],
      patterns: [_re(r'\bllama\b'), _re(r'\bmeta[/-]')],
    ),
    ModelVendorInfo(
      key: 'microsoft',
      label: 'Microsoft',
      aliases: const ['microsoft', 'phi', 'azure'],
      patterns: [_re(r'\bphi(?:-[a-z0-9.]+)?\b'), _re(r'\bmicrosoft[/-]')],
    ),
    ModelVendorInfo(
      key: 'amazon',
      label: 'Amazon',
      aliases: const ['amazon', 'aws', 'bedrock', 'nova', 'titan'],
      patterns: [
        _re(r'\bnova\b'),
        _re(r'\btitan\b'),
        _re(r'\bbedrock\b'),
        _re(r'\bamazon[./-]'),
        _re(r'\baws[./-]'),
      ],
    ),
    ModelVendorInfo(
      key: 'nvidia',
      label: 'NVIDIA',
      aliases: const ['nvidia', 'nemotron'],
      patterns: [_re(r'\bnemotron\b'), _re(r'\bnvidia[/-]')],
    ),
    ModelVendorInfo(
      key: 'deepseek',
      label: 'DeepSeek',
      aliases: const ['deepseek'],
      patterns: [_re(r'\bdeepseek\b')],
    ),
    ModelVendorInfo(
      key: 'moonshot',
      label: 'Moonshot',
      aliases: const ['moonshot', 'moonshotai', 'kimi'],
      patterns: [_re(r'\bmoonshot\b'), _re(r'\bkimi\b')],
    ),
    ModelVendorInfo(
      key: 'zhipu',
      label: 'Zhipu',
      labelZh: '智谱',
      aliases: const ['zhipu', 'zhipuai', 'glm', 'chatglm', 'bigmodel'],
      patterns: [
        _re(r'\bglm(?:-[a-z0-9.]+)?\b'),
        _re(r'\bchatglm\b'),
        _re(r'\bcharglm\b'),
        _re(r'\bcogview\b'),
        _re(r'\bcogvideo\b'),
      ],
    ),
    ModelVendorInfo(
      key: 'minimax',
      label: 'MiniMax',
      aliases: const ['minimax'],
      patterns: [_re(r'\bminimax\b'), _re(r'\babab\b'), _re(r'\bhailuo\b')],
    ),
    ModelVendorInfo(
      key: 'bytedance',
      label: 'ByteDance',
      labelZh: '字节跳动',
      aliases: const ['bytedance', 'volcengine', 'doubao', 'seed'],
      patterns: [
        _re(r'\bdoubao\b'),
        _re(r'\bseed\b'),
        _re(r'\bseedream\b'),
        _re(r'\bseedance\b'),
        _re(r'\bbytedance\b'),
        _re(r'\bvolcengine\b'),
      ],
    ),
    ModelVendorInfo(
      key: 'tencent',
      label: 'Tencent',
      labelZh: '腾讯',
      aliases: const ['tencent', 'hunyuan'],
      patterns: [_re(r'\bhunyuan\b'), _re(r'\btencent[/-]')],
    ),
    ModelVendorInfo(
      key: 'longcat',
      label: 'LongCat',
      aliases: const ['longcat'],
      patterns: [_re(r'\blongcat\b')],
    ),
    ModelVendorInfo(
      key: 'mistral',
      label: 'Mistral',
      aliases: const ['mistral', 'mistralai'],
      patterns: [
        _re(r'\bmistral\b'),
        _re(r'\bmixtral\b'),
        _re(r'\bministral\b'),
        _re(r'\bmagistral\b'),
        _re(r'\bcodestral\b'),
        _re(r'\bdevstral\b'),
        _re(r'\bpixtral\b'),
      ],
    ),
    ModelVendorInfo(
      key: 'alibaba',
      label: 'Alibaba',
      labelZh: '阿里巴巴',
      aliases: const [
        'alibaba',
        'dashscope',
        'qwen',
        'qwq',
        'qvq',
        'tongyi',
        'wanx',
      ],
      patterns: [
        _re(r'\bqwen'),
        _re(r'\bqwq\b'),
        _re(r'\bqvq\b'),
        _re(r'\btongyi\b'),
        _re(r'\bwanx\b'),
      ],
    ),
    ModelVendorInfo(
      key: 'xai',
      label: 'xAI',
      iconIsMonochrome: true,
      aliases: const ['xai', 'grok'],
      patterns: [_re(r'\bgrok\b')],
    ),
    ModelVendorInfo(
      key: 'xiaomi',
      label: 'Xiaomi',
      labelZh: '小米',
      iconIsMonochrome: true,
      aliases: const ['xiaomi', 'mimo', 'xiaomimimo'],
      patterns: [_re(r'\bmimo\b'), _re(r'\bxiaomi\b')],
    ),
    ModelVendorInfo(
      key: 'iflytek',
      label: 'iFlytek',
      labelZh: '讯飞',
      aliases: const ['iflytek', 'iflytekcloud', 'spark'],
      patterns: [_re(r'\bspark\b'), _re(r'\biflytek\b'), _re(r'讯飞|星火')],
    ),
    ModelVendorInfo(
      key: 'stepfun',
      label: 'StepFun',
      labelZh: '阶跃星辰',
      aliases: const ['stepfun', 'step'],
      patterns: [
        _re(r'\bstep(?:-[a-z0-9.]+)?\b'),
        _re(r'\bstepfun\b'),
        _re(r'阶跃星辰'),
      ],
    ),
    ModelVendorInfo(
      key: 'baichuan',
      label: 'Baichuan',
      labelZh: '百川',
      aliases: const ['baichuan'],
      patterns: [_re(r'baichuan'), _re(r'百川')],
    ),
    ModelVendorInfo(
      key: 'baidu',
      label: 'Baidu',
      labelZh: '百度',
      aliases: const ['baidu', 'ernie', 'wenxin', 'qianfan'],
      patterns: [
        _re(r'\bernie\b'),
        _re(r'\bwenxin\b'),
        _re(r'\bbaidu[/-]'),
        _re(r'文心|百度'),
      ],
    ),
    ModelVendorInfo(
      key: 'cohere',
      label: 'Cohere',
      aliases: const ['cohere'],
      patterns: [
        _re(r'\bcommand(?:-[a-z0-9.]+)?\b'),
        _re(r'\bcohere[/-]'),
        _re(r'\baya\b'),
      ],
    ),
    ModelVendorInfo(
      key: 'perplexity',
      label: 'Perplexity',
      aliases: const ['perplexity', 'pplx'],
      patterns: [_re(r'\bsonar\b'), _re(r'\bpplx\b'), _re(r'\bperplexity\b')],
    ),
    ModelVendorInfo(
      key: 'yi',
      label: '01.AI',
      labelZh: '零一万物',
      aliases: const ['yi', '01-ai', '01ai', 'zeroone', 'lingyiwanwu'],
      patterns: [_re(r'\byi-'), _re(r'零一万物')],
    ),
    ModelVendorInfo(
      key: 'internlm',
      label: 'InternLM',
      aliases: const ['internlm', 'intern'],
      patterns: [_re(r'\binternlm'), _re(r'\binternvl\b')],
    ),
    // 聚合方排在最后，避免抢占具体厂商的匹配。
    ModelVendorInfo(
      key: 'openrouter',
      label: 'OpenRouter',
      iconIsMonochrome: true,
      aliases: const ['openrouter'],
      patterns: [_re(r'\bopenrouter\b')],
    ),
    ModelVendorInfo(
      key: 'copilot',
      label: 'GitHub Copilot',
      aliases: const ['copilot', 'github'],
      patterns: [_re(r'\bcopilot\b'), _re(r'\bgithub\b')],
    ),
  ];

  static final Map<String, ModelVendorInfo> _byKey = {
    for (final vendor in vendors) vendor.key: vendor,
  };

  static final Map<String, int> _orderByKey = {
    for (var i = 0; i < vendors.length; i++) vendors[i].key: i,
  };

  static ModelVendorInfo? byKey(String? key) {
    final normalized = key?.trim().toLowerCase() ?? '';
    if (normalized.isEmpty) return null;
    return _byKey[normalized];
  }

  /// 分组排序权重：目录顺序优先，未识别（other）排最后。
  static int orderOf(String key) {
    return _orderByKey[key.trim().toLowerCase()] ?? vendors.length;
  }

  /// 识别模型所属厂商。
  ///
  /// 优先级：模型 ID 正则匹配 → [ownedBy] → [providerId] → [providerName]，
  /// 声明字段先按别名精确/前缀匹配，再按正则匹配。
  static ModelVendorInfo? resolve(
    String modelId, {
    String? ownedBy,
    String? providerId,
    String? providerName,
  }) {
    final normalizedId = _normalize(modelId);
    final fromId = _findByText(normalizedId);
    if (fromId != null) {
      return fromId;
    }
    for (final declared in [
      _normalize(ownedBy),
      _normalize(providerId),
      _normalize(providerName),
    ]) {
      final match = _findByAlias(declared) ?? _findByText(declared);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  /// [resolve] 的分组包装：未识别返回 [otherGroupKey]。
  static String groupKeyFor(
    String modelId, {
    String? ownedBy,
    String? providerId,
    String? providerName,
  }) {
    return resolve(
          modelId,
          ownedBy: ownedBy,
          providerId: providerId,
          providerName: providerName,
        )?.key ??
        otherGroupKey;
  }

  static String _normalize(String? value) {
    return value?.trim().toLowerCase() ?? '';
  }

  static ModelVendorInfo? _findByAlias(String value) {
    if (value.isEmpty) return null;
    for (final vendor in vendors) {
      for (final alias in vendor.aliases) {
        if (value == alias ||
            value.startsWith('$alias.') ||
            value.startsWith('$alias-')) {
          return vendor;
        }
      }
    }
    return null;
  }

  static ModelVendorInfo? _findByText(String value) {
    if (value.isEmpty) return null;
    for (final vendor in vendors) {
      if (vendor.patterns.any((pattern) => pattern.hasMatch(value))) {
        return vendor;
      }
    }
    return null;
  }
}
