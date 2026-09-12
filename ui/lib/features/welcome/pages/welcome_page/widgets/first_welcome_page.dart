import 'package:flutter/material.dart';

class FirstWelcomePage extends StatefulWidget {
  final double screenWidth;
  final double screenHeight;

  const FirstWelcomePage({
    Key? key,
    required this.screenWidth,
    required this.screenHeight,
  }) : super(key: key);

  @override
  State<FirstWelcomePage> createState() => _FirstWelcomePageState();
}

class _FirstWelcomePageState extends State<FirstWelcomePage> {
  late AssetImage image1;
  late AssetImage image2;

  @override
  void initState() {
    super.initState();
    image1 = AssetImage('assets/welcome/welcome_png_1_1.png');
    image2 = AssetImage('assets/welcome/welcome_png_1_2.png');
  }

  @override
  void dispose() {
    image1.evict();
    image2.evict();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          margin: const EdgeInsets.fromLTRB(0, 135, 0, 0),
          width: 135,                            // 撑满父布局宽度
          child: Image(
            image: image1,
            fit: BoxFit.contain,               // 避免拉伸变形
            filterQuality: FilterQuality.high, // 放大时更清晰
          ),
        ),
        Container(
          margin: const EdgeInsets.fromLTRB(0, 4, 0, 0),
          width: 200,                            // 撑满父布局宽度
          child: Image(
            image: image2,
            fit: BoxFit.contain,               // 避免拉伸变形
            filterQuality: FilterQuality.high, // 放大时更清晰
          ),
        ),
      ],
    );
  }
}