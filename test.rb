#!/usr/bin/env ruby
# Tests for the automerge plugin.
#
# Required local configuration:
# - gerrit running on 0.0.0.0:29418
# - 2 cloned projects
# - local user must be an owner of these repos

gem "minitest"
require 'minitest/autorun'
require 'json'
require 'open3'

PROJECTS_DIR="~/"
PROJECT1="project1"
PROJECT2="project2"

class TestAutomerge < MiniTest::Test
  HOST = "0.0.0.0"
  PORT = 29418
  GERRIT_SSH = "ssh -p #{PORT} #{HOST}"

  def setup
    clean_local_repo(PROJECT1)
    clean_local_repo(PROJECT2)
    clean_gerrit([PROJECT1, PROJECT2])
  end

  def test_no_topic
    commit_id = create_review(PROJECT1, "review0 on #{PROJECT1}")
    approve_review(commit_id)
    check_status(commit_id, 'MERGED')
  end

  def test_normal_topic_1_repo
    commit_id = create_review(PROJECT1, "review0 on #{PROJECT1}", "topic1")
    check_status(commit_id, 'NEW')
    approve_review(commit_id)
    check_status(commit_id, 'MERGED')
  end

  def test_crossrepo_topic_1_repo
    commit_id = create_review(PROJECT1, "review0 on #{PROJECT1}", "crossrepo/topic1")
    approve_review(commit_id)
    check_status(commit_id, 'MERGED')
  end

  def test_crossrepo_topic_1_repo_over_not_merged_commit
    commit0 = create_review(PROJECT1, "review0 on #{PROJECT1}")
    commit0b = create_review(PROJECT1, "review0b on #{PROJECT1}", "crossrepo/topic1")
    check_label(commit0b, "Code-Review", "-1")
    check_last_message_contains(commit0b, "atomic_review_same_repo.txt")
  end

  def test_normal_topic_2_repos
    commit1 = create_review(PROJECT1, "review1 on #{PROJECT1}", "topic2")
    commit2 = create_review(PROJECT2, "review2 on #{PROJECT2}", "topic2")
    approve_review(commit1)
    check_status(commit1, 'MERGED')
    check_status(commit2, 'NEW')
    approve_review(commit2)
    check_status(commit2, 'MERGED')
  end

  def test_crossrepo_topic_2_repos
    commit1 = create_review(PROJECT1, "review1 on #{PROJECT1}", "crossrepo/topic2")
    commit2 = create_review(PROJECT2, "review2 on #{PROJECT2}", "crossrepo/topic2")
    approve_review(commit1)
    check_status(commit1, 'NEW')
    check_status(commit2, 'NEW')
    approve_review(commit2)
    check_status(commit1, 'MERGED')
    check_status(commit2, 'MERGED')
  end

  def test_two_reviews_with_same_changed_id
    commit1 = create_review(PROJECT1, "review1 on #{PROJECT1}")
    change_id = read_change_id(PROJECT1)
    abandon_review(commit1)
    # Reuse Change-Id of abandoned review
    commit2 = create_review(PROJECT2, "review2 on #{PROJECT2}", nil, change_id)

    approve_review(commit2)

    check_status(commit2, 'MERGED')
  end

  private

  def project_dir(project_name)
    "#{PROJECTS_DIR}#{project_name}"
  end

  def clean_local_repo(project_name)
    execute("cd #{project_dir(project_name)} && git fetch && git reset --hard FETCH_HEAD")
  end

  def clean_gerrit(projects)
    projects_query = projects.map{|project| "project:#{project}" }.join(" OR ")
    query = "status:open AND (#{projects_query})"
    reviews = gerrit_query(query)
    reviews.each do |review|
      review_number = review['number']
      execute("#{GERRIT_SSH} gerrit review --abandon #{review_number},1")
    end
  end

  def read_change_id(project_name, commit_id = "HEAD")
    change_id = execute(["cd #{project_dir(project_name)}",
                         "git show #{commit_id} | grep Change-Id | sed 's/^.*Change-Id: \\([Ia-f0-9]*\\)$/\\1/'"
                        ].join(" && "))
    refute(change_id.empty?, "missing change-id")
    change_id
  end

  def create_review(project_name, message, topic = nil, change_id = nil)
    topic_suffix = "/#{topic}" if topic
    message = "#{message}\n\nChange-Id: #{change_id}" if change_id
    execute(["cd #{project_dir(project_name)}",
             "echo 0 >> a",
             "git add .",
             %Q(git commit -m "#{message}"),
             "git push origin HEAD:refs/for/master#{topic_suffix}"
            ].join(" && "))
    commit_id = execute("cd #{project_dir(project_name)} && git rev-parse HEAD")
    refute(commit_id.empty?, "missing commit-id")
    commit_id
  end

  def approve_review(commit_id)
    execute("#{GERRIT_SSH} gerrit review --verified 1 --code-review 2 #{commit_id}")
  end

  def abandon_review(commit_id)
    execute("#{GERRIT_SSH} gerrit review --abandon #{commit_id}")
  end

  def check_status(commit_id, expected_status)
    reviews = gerrit_query("commit:#{commit_id}")
    assert_equal(1, reviews.size, "missing review with commit #{commit_id}")
    review = reviews[0]
    assert_equal(expected_status, review['status'], "wrong status on review: #{review['number']}")
  end

  def check_label(commit_id, label_name, expected_label_value)
    reviews = gerrit_query("commit:#{commit_id}", "--all-approvals")
    assert_equal(1, reviews.size, "missing review with commit #{commit_id}")
    review = reviews[0]
    code_review_approvals = review['patchSets'][0]['approvals'].select {|ap| ap['description'] == "Code-Review"}
    refute(code_review_approvals.empty?)
    assert_equal(expected_label_value, code_review_approvals[0]['value'], "wrong label on review: #{review['number']}")
  end

  def check_last_message_contains(commit_id, expected_content)
    reviews = gerrit_query("commit:#{commit_id}", "--comments")
    assert_equal(1, reviews.size, "missing review with commit #{commit_id}")
    messages = reviews[0]['comments'].map{|comment| comment['message'] }
    assert(messages.last.include?(expected_content), "missing comment containing '#{expected_content}'")
  end

  def gerrit_query(query, options = "")
    jsons = `#{GERRIT_SSH} gerrit query --format JSON #{options} '#{query}' | grep -v type\\"\\:\\"stats`
    hashes = []
    jsons.each_line do |line|
      hashes << JSON.parse(line)
    end
    hashes
  end

  # Run a command as a child process and wait for its end.
  def execute(command, opts={})
    _, stdout, stderr, wait_thr = Open3.popen3(command, opts)
    if wait_thr.value != 0
      puts "Command failed: #{command}: stdout: #{stdout.read}, stderr: #{stderr.read}"
      raise RuntimeError, stderr.read
    end
    return stdout.read
  end
end
