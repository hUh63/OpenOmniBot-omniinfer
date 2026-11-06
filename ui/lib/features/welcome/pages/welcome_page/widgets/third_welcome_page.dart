import 'package:flutter/material.dart';

class ThirdWelcomePage extends StatefulWidget {
  final double screenWidth;
  final double screenHeight;

  const ThirdWelcomePage({
    Key? key,
    required this.screenWidth,
    required this.screenHeight,
  }) : super(key: key);

  @override
  State<ThirdWelcomePage> createState() => _ThirdWelcomePageState();
}

class _ThirdWelcomePageState extends State<ThirdWelcomePage> {
  late AssetImage image1;
  late AssetImage image2;
  late AssetImage image3;

  @override
  void initState() {
    super.initState();
    image1 = AssetImage('assets/welcome/welcome_png_3_1.png');
    image2 = AssetImage('assets/welcome/welcome_png_3_2.png');
    image3 = AssetImage('assets/welcome/welcome_png_3_3.png');
  }

  @override
  void dispose() {
    image1.evict();
    image2.evict();
    image3.evict();
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
              width: 327,
              child: Image(
                image: image1,
                fit: BoxFit.contain,
                filterQuality: FilterQuality.high,
              ),
            ),
          ],
        ),

        Positioned(
          top: 160,
          left: 24,
          right: 24,
          child: Container(
            width: 293,
            child: Image(
              image: image2,
              fit: BoxFit.contain,
              filterQuality: FilterQuality.high,
            ),
          ),
        ),

        Positioned(
          top: 386,
          left: 240,
          child: Container(
            width: 91,
            child: Image(
              image: image3,
              fit: BoxFit.contain,
              filterQuality: FilterQuality.high,
            ),
          ),
        ),
      ],
    );
  }
}
