# Plan to fix Song Click and Mini Player

## 1. Verify `SongAdapter` Click Listener
- Ensure `SongAdapter` correctly triggers the `onSongClick` callback.
- Check if `onBindViewHolder` sets the click listener on `holder.itemView`.
- *Status:* Verified in code, looks correct.

## 2. Debug `MainActivity.playSong`
- Add logs to `playSong` to see if it's being called.
- Ensure `miniPlayer.visibility = View.VISIBLE` is executed on the UI thread.
- Check if `musicService` is non-null when `playSong` is called.

## 3. Improve `MusicService.playSong`
- The current implementation of `setOnCompletionListener` in `MainActivity` might be lost because `mediaPlayer` is released and recreated in `MusicService.playSong`.
- Move the completion listener setup inside `MusicService.playSong` or provide a more robust way to handle it.

## 4. Fix Mini Player Visibility
- Ensure the `miniPlayer` ID in `activity_main.xml` matches the one in `MainActivity`.
- Verify that `miniPlayer` is not covered by other views (though `ConstraintLayout` seems okay).

## 5. Address Service Binding Race Condition
- `checkPermissionAndLoadAll()` is called in `onCreate`. If it loads songs immediately and the user clicks one before `onServiceConnected` is called, `musicService` will be null.
- Add a check or disable clicks until `isBound` is true.

## 6. Implementation Steps
1. Add logging to `MainActivity.playSong`.
2. Update `MusicService` to handle completion listeners better.
3. Ensure `miniPlayer` visibility changes are effective.
4. Add null checks for `musicService` in `MainActivity`.
