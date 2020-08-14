jh=$(/usr/libexec/java_home -v 1.8)
jh11=$(/usr/libexec/java_home)
$jh/bin/javapackager -deploy -native dmg -srcfiles build/libs/crescentcash.jar -outdir builds/macOS -name "Crescent Cash" -title "Crescent Cash" -Bmac.CFBundleName=crescentcash -Bruntime="$jh11/../../" -Bicon=icon.icns -outfile crescentcash -appclass app.crescentcash.src.JavaFx11

