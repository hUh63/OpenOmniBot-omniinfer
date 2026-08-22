import 'package:flutter/material.dart';

class BottomSheetBg extends StatelessWidget {
  final Widget child;

  const BottomSheetBg({
    super.key,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).padding.bottom,
      ),
      width: double.infinity,
      decoration: ShapeDecoration(
        color: Colors.white,
        // gradient: LinearGradient(
        //   begin: Alignment(0.50, -0.20),
        //   end: Alignment(0.50, 0.44),
        //   colors: [const Color(0x6581B8FF), const Color(0x006BF0FF)],
        // ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(8),
            topRight: Radius.circular(8),
          ),
        ),
        shadows: [
          BoxShadow(
            color: Color(0x26000000),
            blurRadius: 4,
            offset: Offset(0, 0),
            spreadRadius: 0,
          )
        ],
      ),
      child: Stack(
        children: [
          Positioned(
            left: 0,
            right: 0,
            top: 0,
            child: Container(
              height: 146,
              decoration: ShapeDecoration(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.only(
                    topLeft: Radius.circular(8),
                    topRight: Radius.circular(8),
                  ),
                ),
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [const Color(0x9A4DA3FF), const Color(0xFFFFFFFF)],
                ),
              ),
            ),
          ),
          child,
        ],
      ),
    );
  }
}