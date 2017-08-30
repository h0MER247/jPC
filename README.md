# jPC - A PC Emulator written in Java

### Features
 - Emulation of the Intel 8086 as well as the 80386 CPU. Decoded instruction blocks get cached to speed up the emulation.
 - Programmable Peripheral Interface i8255
 - Interrupt Controller i8259
 - Programmable Interval Timer i8253
 - UART 16450 (Serial port)
 - Basic IDE and VGA emulation
 - PC Speaker output
 - Serial and PS/2 Mouse emulation
 
### Screenshots
<p align="center">
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408214/7f0f5730-409d-11e7-8084-85cd92669ed8.png" width="18%" alt="Main screen" title="Main screen" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408211/7f06eece-409d-11e7-977a-6efa73c00a58.png" width="18%" alt="Booting" title="Booting" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408215/7f229e58-409d-11e7-8269-cb30a35b4d1f.png" width="18%" alt="Microsoft Defrag" title="Microsoft Defrag" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408212/7f0a7184-409d-11e7-9b2b-75bfa75e64cc.png" width="18%" alt="MS-DOS Qbasic" title="MS-DOS Qbasic" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408213/7f0f49e8-409d-11e7-9b76-fe9e3fbda8fc.png" width="18%" alt="Gorilla" title="Gorilla" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408234/7fabac70-409d-11e7-8b2a-1dd769338413.png" width="18%" alt="Commander Keen 1" title="Commander Keen 1" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408218/7f2b8522-409d-11e7-8152-6ae7730b5f54.png" width="18%" alt="Commander Keen 1" title="Commander Keen 1" >
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408216/7f25e1a8-409d-11e7-8fe2-90989557a3cf.png" width="18%" alt="Commander Keen 4" title="Commander Keen 4" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408217/7f2a0e68-409d-11e7-97a6-56192c023eb1.png" width="18%" alt="Commander Keen 4" title="Commander Keen 4" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408219/7f2c130c-409d-11e7-9ed3-cc057ebad622.png" width="18%" alt="Commander Keen 4" title="Commander Keen 4" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408228/7f77f65a-409d-11e7-92ff-782b36b17b90.png" width="18%" alt="Commander Keen Dreams" title="Commander Keen Dreams" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408229/7f7b2596-409d-11e7-8f16-ae8df193670d.png" width="18%" alt="Commander Keen Dreams" title="Commander Keen Dreams" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408220/7f400272-409d-11e7-8158-611d5910ea73.png" width="18%" alt="Zak McKracken" title="Zak McKracken" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408221/7f4564ba-409d-11e7-91ec-27a76aba802b.png" width="18%" alt="Zak McKracken" title="Zak McKracken" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408222/7f50e812-409d-11e7-9c98-4671290573c8.png" width="18%" alt="Indiana Jones 3 - The Last Crusade" title="Indiana Jones 3 - The Last Crusade" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408223/7f5a5974-409d-11e7-8993-29726884cf90.png" width="18%" alt="Sim City" title="Sim City" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408224/7f5b46b8-409d-11e7-9c31-3026ee87c74b.png" width="18%" alt="Sim City" title="Sim City" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408225/7f5d7cf8-409d-11e7-892f-47fc33d361eb.png" width="18%" alt="Dyna Blaster" title="Dyna Blaster" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408226/7f619248-409d-11e7-8d38-4acc05998225.png" width="18%" alt="Winter Games" title="Winter Games" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408227/7f6b56f2-409d-11e7-9792-854e980d1639.png" width="18%" alt="Jetpack" title="Jetpack" />
  <img src="https://user-images.githubusercontent.com/5739639/29886259-c88cc8ac-8db9-11e7-9d0d-6ac4f2389994.png" width="18%" alt="Mortal Kombat 3" title="Mortal Kombat 3" />
  <img src="https://user-images.githubusercontent.com/5739639/29886262-cc708cc4-8db9-11e7-870d-96d6ca91ddd6.png" width="18%" alt="Sim City 2000" title="Sim City 2000" />
  <img src="https://user-images.githubusercontent.com/5739639/29886263-cc870b7a-8db9-11e7-9eaf-a3d7d5de1d53.png" width="18%" alt="The Settlers II" title="The Settlers II" />
  <img src="https://user-images.githubusercontent.com/5739639/29886282-d44f6654-8db9-11e7-94f4-db3fd3a69dad.png" width="18%" alt="Turrican 2" title="Turrican 2" />
  <img src="https://user-images.githubusercontent.com/5739639/29886279-d446ef74-8db9-11e7-8898-f01c0b6f9e45.png" width="18%" alt="Windows 95" title="Windows 95" />
</p>

### Example Drive Image
 - [jPC_HDD.zip] contains an preinstalled [Caldera Open DOS 7.01], DOS Controller and some shareware classics like CD Man, Commander Keen, Dr. Rudy, Jetpack, Jill Of The Jungle, Overkill and others.
 
### Prerequisites
As I have no interest in getting into any kind of copyright related issues, you have to provide the following ROM files yourself.
 - Tseng ET4000 BIOS ROM<br />
   (Required file: [et4000.bin])
 - IBM Basic C1.10 ROM<br />
   (Optional file: [basicc11.bin])
   
### Starting
 - Compile the project
 - Place et4000.bin (and optionally basicc11.bin) in the same directory as jPC.jar or put them in the respective folders at src/Hardware/ROM/... before you compile the project.
 
### Compiled project
 - You'll find the [compiled] project in the corresponding issues thread.
 
### Useful Tools
 - [WinImage], for manipulating floppy- and hard disk images.
 
### BIOS Roms
This project uses the [Super PC/Turbo XT BIOS 3.0] rom as well as a self compiled version from the bochs bios that is made to run on 386 systems.
 
### Known Issues
  - Most of the special registers and behaviors of the Tseng ET4000 graphics card aren't implemented at all at the moment. Especially 16 and 24 bit color modes!
  - There is no FDC emulation at the moment. That means no floppy disks can be mounted atm.
  - CLI and STI opcodes aren't implemented properly.
  - I'm still not entirely sure if the shift and rotate opcodes are implemented correctly for the 8086.
  
Have fun.

[Super PC/Turbo XT BIOS 3.0]:http://www.phatcode.net/downloads.php?id=101
[jPC_HDD.zip]:https://github.com/h0MER247/jPC/files/1025898/jPC_HDD.zip
[Caldera Open DOS 7.01]:http://ftp.uni-bayreuth.de/pc/caldera/OpenDOS.701/
[WinImage]:http://www.winimage.com/download.htm
[et4000.bin]:https://www.google.com/?#q=%22et4000.bin%22!+file
[basicc11.bin]:https://www.google.com/?#q=%22basicc11.bin%22!+file
[pcxtbios.bin]:http://www.phatcode.net/downloads.php?id=101
[compiled]:https://github.com/h0MER247/jPC/issues/3
