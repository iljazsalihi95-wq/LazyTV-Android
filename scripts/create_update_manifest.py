#!/usr/bin/env python3
"""Release metadata: APK hash/version are read from the built artifact, never guessed."""
import hashlib,json,pathlib,subprocess,sys,re,os
apk=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2])
sdk=pathlib.Path(os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME','/usr/local/lib/android/sdk')))
aapt=sdk/'build-tools/35.0.0/aapt'
badging=subprocess.check_output([str(aapt),'dump','badging',str(apk)],text=True)
package=re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'",badging)
if not package or package[1]!='de.lazytv.pro':raise SystemExit('Unexpected APK package')
version=int(package[2]);sha=hashlib.sha256(apk.read_bytes()).hexdigest()
out.write_text(json.dumps({'versionCode':version,'versionName':package[3],'apkUrl':f'https://github.com/iljazsalihi95-wq/LazyTV-Android/releases/download/v{version}/LazyTV-PRO.apk','sha256':sha},indent=2)+'\n')
