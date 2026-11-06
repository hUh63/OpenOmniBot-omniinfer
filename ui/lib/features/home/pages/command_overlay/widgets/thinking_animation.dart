import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';


class ThinkingAnimation extends StatefulWidget {
  final bool isThinking;

  const ThinkingAnimation({
    Key? key,
    this.isThinking = true,
  }) : super(key: key);

  @override
  State<ThinkingAnimation> createState() => _ThinkingAnimationState();
}

class _ThinkingAnimationState extends State<ThinkingAnimation> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    );

    _animation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );

    if (widget.isThinking) {
      _controller.repeat(reverse: true);
    }
  }

  @override
  void didUpdateWidget(ThinkingAnimation oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isThinking != oldWidget.isThinking) {
      if (widget.isThinking) {
        _controller.repeat(reverse: true);
      } else {
        // Smoothly animate back to start (static state)
        _controller.animateTo(0.0);
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 16,
      height: 16,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Ball 1 (Left to Right)
          AnimatedBuilder(
            animation: _animation,
            builder: (context, child) {
              final offset = 5.13 * _animation.value;
              return Transform.translate(
                offset: Offset(offset, 0),
                child: child,
              );
            },
            child: Align(
              alignment: const Alignment(-0.64, 0.0), 
              child: SvgPicture.asset(
                'assets/chatbot/ball1.svg',
                width: 8.13,
                height: 8.13, 
              ),
            ),
          ),
          // Ball 2 (Right to Left)
          AnimatedBuilder(
            animation: _animation,
            builder: (context, child) {
              final offset = -5.13 * _animation.value;
              return Transform.translate(
                offset: Offset(offset, 0),
                child: child,
              );
            },
            child: Align(
              alignment: const Alignment(0.64, 0.0),
              child: SvgPicture.asset(
                'assets/chatbot/ball2.svg',
                width: 8.13,
                height: 8.13,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
