import 'package:flutter/material.dart';

class SecondWelcomePage extends StatefulWidget {
  final double screenWidth;
  final double screenHeight;

  const SecondWelcomePage({
    Key? key,
    required this.screenWidth,
    required this.screenHeight,
  }) : super(key: key);

  @override
  State<SecondWelcomePage> createState() => _SecondWelcomePageState();
}

class _SecondWelcomePageState extends State<SecondWelcomePage> {
  late AssetImage image1;
  late AssetImage image2;

  @override
  void initState() {
    super.initState();
    image1 = AssetImage('assets/welcome/welcome_png_2_1.png');
    image2 = AssetImage('assets/welcome/welcome_png_2_2.png');
  }

  @override
  void dispose() {
    image1.evict();
    image2.evict();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // 主内容：Column 居中排列的三个图片
        Column(
          children: [
            Container(
              margin: const EdgeInsets.fromLTRB(24, 44, 24, 0),
              width: 327,                            // 撑满父布局宽度
              child: Image(
                image: image1,
                fit: BoxFit.contain,               // 避免拉伸变形
                filterQuality: FilterQuality.high, // 放大时更清晰
              ),
            ),
          ],
        ),

        Positioned(
          top: 160,
          left: 24,
          right: 24,
          child: Container(
            width: 327,
            child: Image(
              image: image2,
              fit: BoxFit.contain,
              filterQuality: FilterQuality.high,
            ),
          ),
        ),
      ],
    );
  }
}