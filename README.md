# jPC - A PC/XT Emulator written in Java

### Features
 - Emulation of the Intel 8086 CPU. Decoded instruction blocks get cached to speed up the emulation.
 - Programmable Peripheral Interface i8255
 - Interrupt Controller i8259
 - Programmable Interval Timer i8253
 - Basic VGA emulation
 - PC Speaker output
 
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
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408237/80b7fefc-409d-11e7-9b8d-556523854ba1.png" width="18%" alt="Jill Of The Jungle" title="Jill Of The Jungle" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408231/7f894cca-409d-11e7-99ef-fbe55d56b14d.png" width="18%" alt="The (even more) Incredible Machine" title="The (even more) Incredible Machine" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408230/7f877c10-409d-11e7-83da-fe120927d1aa.png" width="18%" alt="Microsoft Windows 3.0 (with a patched vga driver from http://www.vcfed.org)" title="Microsoft Windows 3.0 (with a patched vga driver from http://www.vcfed.org)" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408232/7f926fc6-409d-11e7-99be-1f68419d8c87.png" width="18%" alt="Solitaire" title="Solitaire" />
  <img src="https://cloud.githubusercontent.com/assets/5739639/26408233/7f958134-409d-11e7-9241-a82e1d7a68d0.png" width="18%" alt="Superscape VGA Benchmark v1.0" title="Superscape VGA Benchmark v1.0" />
</p>

### Example Drive Image
 - [jPC_HDD.zip] contains an preinstalled [Caldera Open DOS 7.01], DOS Controller and some shareware classics like CD Man, Commander Keen, Dr. Rudy, Jetpack, Jill Of The Jungle, Overkill, ...
 
### Prerequisites
As I have no interest in getting into any kind of copyright related issues, you have to provide the following ROM files yourself.
 - This project requires the Tseng ET4000 BIOS ROM<br />
   (Required file: [et4000.bin])
 - This project requires the modified "Super PC/Turbo XT BIOS 3.0". The original IBM XT BIOS will not work on this emulator.<br />
   (Required file: [pcxtbios.bin])
 - The IBM Basic C1.10 ROM however is optional<br />
   (Optional file: [basicc11.bin])
   
### Starting
 - Compile the project
 - Place et4000.bin and pcxtbios.bin (and optionally basicc11.bin) in the same directory as jPC.jar or put them in the respective folders at src/Hardware/ROM/ before you compile the project.
 
### Useful Tools
 - [WinImage], for manipulating floppy- and hard disk images.
 
### Known Issues
  - The special registers and behaviors of the Tseng ET4000 graphics card aren't implemented at all at the moment. The only thing that is (partially) implemented is the VGA standard.
  - There is no FDC or IDE emulation at the moment. Floppy and hard drives get emulated through hooking of interrupt 13h. Software, that requires access to the FDC or IDE i/o-ports, will not function correctly (or even at all).
  - CLI and STI opcodes aren't implemented properly.
  - I'm not entirely sure if the shift and rotate opcodes are implemented correctly for the 8086. I took the code for these opcodes from
    my 80386 emulator. The 8086 handles these opcodes slightly different than a 80386 and I have no real hardware to test this atm.
  
Have fun.

[Super PC/Turbo XT BIOS 3.0]:http://www.phatcode.net/downloads.php?id=101
[jPC_HDD.zip]:https://github.com/h0MER247/jPC/files/1025898/jPC_HDD.zip
[Caldera Open DOS 7.01]:http://ftp.uni-bayreuth.de/pc/caldera/OpenDOS.701/
[WinImage]:http://www.winimage.com/download.htm
[et4000.bin]:https://www.google.com/?#q=%22et4000.bin%22!+file
[basicc11.bin]:https://www.google.com/?#q=%22basicc11.bin%22!+file
[pcxtbios.bin]:http://www.phatcode.net/downloads.php?id=101
