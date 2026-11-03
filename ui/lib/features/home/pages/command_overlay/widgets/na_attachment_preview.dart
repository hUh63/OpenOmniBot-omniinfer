import 'package:flutter/material.dart';
import 'package:ui/services/agent_runtime_service.dart';

/// Cloud images use the same account-scoped native file reader as the workspace.
/// No credentials, public download links or phone-local paths enter history.
class NaAttachmentPreview extends StatefulWidget {
  const NaAttachmentPreview({
    super.key,
    required this.attachment,
    required this.child,
  });
  final Map<String, dynamic> attachment;
  final Widget child;

  @override
  State<NaAttachmentPreview> createState() => _NaAttachmentPreviewState();
}

class _NaAttachmentPreviewState extends State<NaAttachmentPreview> {
  Future<CodexRemoteFilePayload>? _image;

  void _load() {
    final item = widget.attachment;
    _image =
        item['deleted'] != true &&
            (item['mimeType'] as String? ?? '').startsWith('image/')
        ? AgentRuntimeService.readRemoteFile(
            agentId: 'na-cloud',
            sessionId: item['sessionId'] as String,
            path: item['path'] as String,
            remoteCwd: '/workspace',
          )
        : null;
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant NaAttachmentPreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.attachment['path'] != widget.attachment['path'] ||
        oldWidget.attachment['sessionId'] != widget.attachment['sessionId'] ||
        oldWidget.attachment['deleted'] != widget.attachment['deleted']) {
      _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_image == null) return widget.child;
    return FutureBuilder<CodexRemoteFilePayload>(
      future: _image,
      builder: (context, snapshot) {
        final bytes = snapshot.data?.bytes;
        if (bytes == null || snapshot.data?.ok != true) return widget.child;
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: Image.memory(
                bytes,
                width: 120,
                height: 100,
                fit: BoxFit.cover,
                cacheWidth: 240,
                errorBuilder: (_, _, _) => const SizedBox.shrink(),
              ),
            ),
            widget.child,
          ],
        );
      },
    );
  }
}
