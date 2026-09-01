import 'package:flutter/material.dart';
import 'package:photo_manager/photo_manager.dart';

void main() {
  runApp(const MyGalleryApp());
}

// 1. PINAKAILALIM NA PATONG: Ang App Frame (Parang <html> tag)
class MyGalleryApp extends StatelessWidget {
  const MyGalleryApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(), // Binibigyan agad ng modernong Dark Theme ang app
      home: const GalleryScreen(),
    );
  }
}

class GalleryScreen extends StatefulWidget {
  const GalleryScreen({super.key});

  @override
  State<GalleryScreen> createState() => _GalleryScreenState();
}

class _GalleryScreenState extends State<GalleryScreen> {
  // Dito natin itatago ang listahan ng mga litrato na makukuha sa phone (Parang JS Array)
  List<AssetEntity> mgaLitrato = [];
  bool isLoading = true;

  @override
  void initState() {
    super.initState();
    humingiNgPermiso(); // Awtomatikong tatakbo pagbukas ng app
  }

  // NATIVE LOGIC: Pakikipag-usap sa Android MediaStore API (Parang JS Async/Await)
  Future<void> humingiNgPermiso() async {
    final PermissionState permiso = await PhotoManager.requestPermissionExtended();
    
    if (permiso.isAuth) {
      // Kung pinayagan ng user, kukunin natin ang mga Albums
      List<AssetPathEntity> albums = await PhotoManager.getAssetPathList(
        type: RequestType.image, // Litrato lang muna ang kukunin natin
      );

      if (albums.isNotEmpty) {
        // Kunin ang pinakabagong 60 na litrato
        List<AssetEntity> media = await albums[0].getAssetListRange(start: 0, end: 60);
        setState(() {
          mgaLitrato = media;
          isLoading = false;
        });
      }
    } else {
      // Kung tinanggihan, bubuksan ang settings ng phone para i-allow manu-mano
      PhotoManager.openSetting();
    }
  }

  // 2. PAGBUO NG INTERFACE (Ang pagpapatong-patong ng Widgets)
  @override
  Widget build(BuildContext context) {
    return Scaffold( // IPATONG ANG CANVAS (Ang basehan ng screen)
      appBar: AppBar( // IPATONG ANG HEADER (Parang <header> sa HTML)
        title: const Text('Aking Native Album'),
        elevation: 2,
      ),
      // LOGIC KUNG LOADING O TAPOS NA
      body: isLoading 
          ? const Center(child: CircularProgressIndicator()) // Spinner habang nag-iiscan
          : GridView.builder( // IPATONG ANG GRID LAYOUT (Parang CSS Grid)
              padding: const EdgeInsets.all(4.0),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3, // 3 Columns na litrato
                crossAxisSpacing: 4.0, // Gap sa pagitan ng columns (Parang gap sa CSS)
                mainAxisSpacing: 4.0,  // Gap sa pagitan ng rows
              ),
              itemCount: mgaLitrato.length,
              itemBuilder: (context, index) {
                // IPATONG ANG MISMONG LITRATO (Parang <img> tag na naka-optimize ang memory)
                return GestureDetector(
                  onTap: () {
                    // Dito pwede mong ilagay ang fullscreen code kapag pinindot ang litrato
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Pinindot mo ang litrato #$index')),
                    );
                  },
                  child: AssetEntityImage(
                    mgaLitrato[index],
                    isOriginal: false, // Thumbnail lang para mabilis at hindi mag-lag
                    thumbnailSize: const ThumbnailSize.square(200),
                    fit: BoxFit.cover, // Parang object-fit: cover sa CSS
                  ),
                );
              },
            ),
    );
  }
}