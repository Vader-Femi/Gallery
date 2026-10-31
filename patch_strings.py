import sys
with open('app/src/main/res/values/strings.xml', 'r') as f:
    content = f.read()

append_str = """
    <!-- Groups -->
    <string name="settings_group_general">General</string>
    <string name="settings_group_albums">Albums &amp; Folders</string>
    <string name="settings_group_video">Video Playback</string>
    <string name="settings_group_indexing">Search &amp; Indexing</string>

    <string name="settings_album_sort">Album sort order</string>
    <string name="settings_album_sort_summary">Choose how albums are ordered in the Albums tab</string>
    <string name="settings_album_sort_recency">Most recent</string>
    <string name="settings_album_sort_name">Name</string>
    <string name="settings_album_sort_count">Item count</string>

    <string name="settings_hide_albums">Hide albums</string>
    <string name="settings_hide_albums_summary">Choose which albums to hide from the Albums tab</string>
    <string name="settings_hide_albums_dialog_title">Select albums to hide</string>

    <string name="settings_delete_empty_albums">Delete empty albums</string>
    <string name="settings_delete_empty_albums_summary">Find and delete empty folders in your storage</string>
    <string name="settings_delete_empty_albums_deleted">Deleted %1$d empty albums.</string>
    <string name="settings_delete_empty_albums_none">No empty albums found.</string>
</resources>
"""
content = content.replace("</resources>", append_str)

with open('app/src/main/res/values/strings.xml', 'w') as f:
    f.write(content)
