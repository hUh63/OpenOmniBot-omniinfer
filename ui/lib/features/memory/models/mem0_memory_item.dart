class Mem0MemoryItem {
  final String id;
  final String memory;
  final String? hash;
  final List<String> topLevelCategories;
  final Map<String, dynamic> metadata;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final String? userId;
  final String? agentId;
  final double? score;

  const Mem0MemoryItem({
    required this.id,
    required this.memory,
    this.hash,
    this.topLevelCategories = const [],
    this.metadata = const {},
    this.createdAt,
    this.updatedAt,
    this.userId,
    this.agentId,
    this.score,
  });

  List<String> get categories {
    if (topLevelCategories.isNotEmpty) {
      return topLevelCategories;
    }
    final topLevel = _asStringList(metadata['categories']);
    if (topLevel.isNotEmpty) {
      return topLevel;
    }
    return const [];
  }

  DateTime? get displayTime => updatedAt ?? createdAt;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'memory': memory,
      'hash': hash,
      'categories': topLevelCategories,
      'metadata': metadata,
      'created_at': createdAt?.toIso8601String(),
      'updated_at': updatedAt?.toIso8601String(),
      'user_id': userId,
      'agent_id': agentId,
      'score': score,
    };
  }

  factory Mem0MemoryItem.fromJson(Map<String, dynamic> json) {
    final metadataRaw = json['metadata'];
    final metadata = metadataRaw is Map<String, dynamic>
        ? metadataRaw
        : metadataRaw is Map
        ? Map<String, dynamic>.from(metadataRaw)
        : <String, dynamic>{};

    return Mem0MemoryItem(
      id: (json['id'] ?? '').toString(),
      memory: (json['memory'] ?? json['text'] ?? json['content'] ?? '')
          .toString(),
      hash: json['hash']?.toString(),
      topLevelCategories: _asStringList(json['categories']),
      metadata: metadata,
      createdAt: _parseDate(json['created_at'] ?? json['createdAt']),
      updatedAt: _parseDate(json['updated_at'] ?? json['updatedAt']),
      userId: json['user_id']?.toString(),
      agentId: json['agent_id']?.toString(),
      score: _parseScore(json['score']),
    );
  }

  static DateTime? _parseDate(dynamic value) {
    if (value == null) {
      return null;
    }
    if (value is int) {
      if (value > 1000000000000) {
        return DateTime.fromMillisecondsSinceEpoch(value);
      }
      return DateTime.fromMillisecondsSinceEpoch(value * 1000);
    }
    if (value is double) {
      return _parseDate(value.toInt());
    }

    final raw = value.toString().trim();
    if (raw.isEmpty) {
      return null;
    }

    final asInt = int.tryParse(raw);
    if (asInt != null) {
      return _parseDate(asInt);
    }

    return DateTime.tryParse(raw);
  }

  static double? _parseScore(dynamic value) {
    if (value == null) {
      return null;
    }
    if (value is double) {
      return value;
    }
    if (value is int) {
      return value.toDouble();
    }
    return double.tryParse(value.toString());
  }

  static List<String> _asStringList(dynamic value) {
    if (value is List) {
      return value
          .map((item) => item.toString().trim())
          .where((item) => item.isNotEmpty)
          .toList();
    }
    return const [];
  }
}

class Mem0MemoryRelation {
  final String source;
  final String relationship;
  final String target;

  const Mem0MemoryRelation({
    required this.source,
    required this.relationship,
    required this.target,
  });

  factory Mem0MemoryRelation.fromJson(Map<String, dynamic> json) {
    return Mem0MemoryRelation(
      source: (json['source'] ?? '').toString(),
      relationship: (json['relationship'] ?? '').toString(),
      target: (json['target'] ?? json['destination'] ?? '').toString(),
    );
  }

  Map<String, dynamic> toJson() {
    return {'source': source, 'relationship': relationship, 'target': target};
  }
}

class Mem0MemorySnapshot {
  final bool configured;
  final List<Mem0MemoryItem> items;
  final List<Mem0MemoryRelation> relations;
  final DateTime? fetchedAt;
  final bool fromCache;
  final bool isStale;
  final String? infoMessage;
  final String? errorMessage;

  const Mem0MemorySnapshot({
    required this.configured,
    this.items = const [],
    this.relations = const [],
    this.fetchedAt,
    this.fromCache = false,
    this.isStale = false,
    this.infoMessage,
    this.errorMessage,
  });

  factory Mem0MemorySnapshot.unconfigured() {
    return const Mem0MemorySnapshot(configured: false);
  }

  bool get hasData => items.isNotEmpty;

  bool get shouldShowSection =>
      configured || hasData || errorMessage != null || infoMessage != null;

  Map<String, dynamic> toCacheJson() {
    return {
      'configured': configured,
      'fetchedAt': fetchedAt?.toIso8601String(),
      'items': items.map((item) => item.toJson()).toList(),
      'relations': relations.map((relation) => relation.toJson()).toList(),
    };
  }

  factory Mem0MemorySnapshot.fromCacheJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    final rawRelations = json['relations'];

    return Mem0MemorySnapshot(
      configured: json['configured'] != false,
      fetchedAt: Mem0MemoryItem._parseDate(json['fetchedAt']),
      items: rawItems is List
          ? rawItems
                .whereType<Map>()
                .map(
                  (item) =>
                      Mem0MemoryItem.fromJson(Map<String, dynamic>.from(item)),
                )
                .toList()
          : const [],
      relations: rawRelations is List
          ? rawRelations
                .whereType<Map>()
                .map(
                  (item) => Mem0MemoryRelation.fromJson(
                    Map<String, dynamic>.from(item),
                  ),
                )
                .toList()
          : const [],
      fromCache: true,
    );
  }

  Mem0MemorySnapshot copyWith({
    bool? configured,
    List<Mem0MemoryItem>? items,
    List<Mem0MemoryRelation>? relations,
    DateTime? fetchedAt,
    bool? fromCache,
    bool? isStale,
    String? infoMessage,
    String? errorMessage,
  }) {
    return Mem0MemorySnapshot(
      configured: configured ?? this.configured,
      items: items ?? this.items,
      relations: relations ?? this.relations,
      fetchedAt: fetchedAt ?? this.fetchedAt,
      fromCache: fromCache ?? this.fromCache,
      isStale: isStale ?? this.isStale,
      infoMessage: infoMessage ?? this.infoMessage,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }
}
