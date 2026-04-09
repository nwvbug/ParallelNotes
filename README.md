<img src="https://raw.githubusercontent.com/nwvbug/ParallelNotes/ea82d6575c1299d96d090cb80bc95dbf8260e47c/Assets/Parallel%20Notes%20Logo.svg" width="200">

# Parallel Notes
*A polished, smooth, and professional feeling notetaking app for Android.*

I have been an Android tablet user for school for a few years now, and while I enjoy the experience, I have noticed that many of the notetaking apps don't take advantage of most Android tablet capabilities.

Unlike iPad tools, they seem to be targeted at the lowest common denominator of device. I want to build a polished notetaking app that takes full advantage of things like pressure sensitivity and pen tilt on higher-end tablets, without becoming a drawing app.

## Why is it better?
### Unique Tools
In addition to the standard set of handwritten notetaking tools like the various pens, erasers, and lassos, Parallel Notes has several tools unique to it.
- **Spotlight Pen**: The spotlight pen allows you to tag and index your notes naturally while you write. Strokes drawn with this pen are automatically added into a folder-level dashboard, allowing you to instantly jump back to key concepts and to-dos across all your notebooks. Spotlight Pen is being tested in real-life use.
- **Note Linking & Embedding**: Inspired from text-based notetaking apps like Obsidian, you will be able to link relevant notes to each other to jump between them, or embed a note into a different one to view them both at the same time. The Note Linking and Embedding features are still in earely development.
- **Parallel**: The namesake feature of the app, allowing you to take nonlinear notes in a more efficient and organized way. Create interactive parts of your note that swap contents when you click them, like being able to swap between examples of a math problem to show. The Parallel feature is still in early development.

### Smoothness
I have implemented already and plan to continue implementing various utilities for smoothing your ink. All strokes are run through a basic bezier curve smooth, but in addition to that, I have recreated Procreate's Streamline feature to eliminate jitter from your writing.

### Speed and Quality
I am using a hybrid approach to my displaying of ink which includes both vector based representations for dragging and editing elements and rasterized bitmap representations for speed of rendering. The result is extremely high quality ink that takes near-zero CPU effort to render.

### Customization
While the app will offer extensive customization, all of these settings will be focused on one thing: notetaking. This is not intended to be a drawing app, and all of the development is intended to make this the smoothest and most satisfying notetaking app ever.

### Sync, in your control
Eventually, I wish to implement cloud sync for your notes. However, this will not go to a central server of mine. You will be able to connect a cloud storage provider that you already use (Google Drive, OneDrive, etc) and sync your notes to your own cloud storage.

## Where can I see it?
Parallel Notes is currently in active, extremely early development. It is not available for demo or download at the moment.
