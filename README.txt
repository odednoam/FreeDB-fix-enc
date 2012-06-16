FreeDB Encoding Fix Project

(c)2012 Oded Noam, oded@no.am

This software project is the infrastructure to a one-time project of locating records in the FreeDB.org CD contents database, that have not been correctly converted to Unicode, and fix them. Detection of charsets is done using open-source detection algorithms, but due to the high rate of false detections, need to be manually validated to be used. 
As this task requires knowledge of many languages, it is done by crowdsourcing - using a web application that can be used by people from anywhere in the world to approve or reject a suggested charset fix.

The fixing process is performed in 3 steps:
1. Identifying problematic entries in freedb.
   The tools for this are written in Java, run locally and are used in two steps:
   1.1 Go over a database dump (.tar.bz2) and filter out entries that are problematic
       (src/FilterOutNonLatin.java)
   1.2 Go over the problematic entries, and try to detect their charset using two open-source charset detection libraries (jcu from IBM, and icu4j from mozilla). Create a list of suggested charset fixes, in JSON format.
       (src/PrepareJsonForAppengine)

2. Crowdsourced validation of the detected charsets.
   This is a web application in which users are shown with suggested charset fixes, and need to validate whether these fixes are correct or not. The application runs in Google AppEngine's python framework, and its source resides in "appengine/" directory.
   To use, follow these steps:
   1.1 Deploy the application to appengine
   1.2 Logged in as administrator, upload the JSON entries database created in step 1. The upload process starts in the URL "/upload" relative to the application home.
   1.3 Have users validate entries.
   1.4 Export the results JSON file, by clicking "extract" in the main menu (logged in as adminsitrator) on each
       language.
   1.5 Download the created JSON file from the blob-storage (in the appengine admin menu) and feed it to step 3.

3. Create database update files
   Once results JSON files are created and download to the local computer, run the java program to create a database fix for the required entries.
   (src/PrepareJsonForAppengine)

This software uses several open source tools, stored under the "libs" direcory.
This software is distributed under the GPL 3.0 license. If you wish to use it or any part of it under a different license, please contact me.

