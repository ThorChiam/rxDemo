# rxDemo

Using Rx to manage the download process:
1. Download EngineModule & ShareModule at the **same time**
2. Download package **after** both modules have been downloaded

So the main screen should show the progress status accordingly and only mark it as completed while all three downloads finished.

*We use Timer to simulate the download process.*

*The callBack is kind of embbed method of the Modules.*
