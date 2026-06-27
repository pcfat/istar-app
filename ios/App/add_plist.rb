#!/usr/bin/env ruby
require 'xcodeproj'

project_path = 'App.xcodeproj'
plist_path = 'App/GoogleService-Info.plist'

project = Xcodeproj::Project.open(project_path)

# Find the main target
target = project.targets.first

# Check if file already exists in project
existing_file = project.files.find { |f| f.path == plist_path }

if existing_file
  puts "GoogleService-Info.plist already exists in project"
  exit 0
end

# Find the App group (where other files are)
app_group = project.main_group.groups.find { |g| g.name == 'App' || g.path == 'App' }
app_group ||= project.main_group

# Add the file reference
file_ref = app_group.new_reference(plist_path)

# Add to target's resources
target.add_resources([file_ref])

# Save the project
project.save

puts "GoogleService-Info.plist added to Xcode project successfully"
