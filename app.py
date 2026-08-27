import json
import os
from flask import (
    Flask,
    jsonify,
    redirect,
    render_template,
    request,
    send_from_directory,
    url_for,
)
from flask_sqlalchemy import SQLAlchemy
from google import genai

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///portfolios_v3.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)

GEMINI_API_KEY = os.environ.get('GEMINI_API_KEY', 'YOUR_GEMINI_API_KEY_HERE')
client = genai.Client(api_key=GEMINI_API_KEY)


class FullPortfolio(db.Model):
  id = db.Column(db.Integer, primary_key=True)
  slug = db.Column(db.String(100), unique=True, nullable=False)
  name = db.Column(db.String(100), nullable=False)
  gender = db.Column(db.String(20), default='male')
  tagline = db.Column(db.String(150))
  email = db.Column(db.String(100), nullable=False)
  github = db.Column(db.String(200))
  linkedin = db.Column(db.String(200))
  bio = db.Column(db.Text)
  education_degree = db.Column(db.String(150))
  education_college = db.Column(db.String(200))
  education_year = db.Column(db.String(50))
  skills_core = db.Column(db.Text)
  skills_tools = db.Column(db.Text)
  projects_json = db.Column(db.Text)
  experience_json = db.Column(db.Text)
  certificates_json = db.Column(db.Text)
  theme = db.Column(db.String(50), default='theme_modern')


with app.app_context():
  db.create_all()


# Service Worker Route for App
@app.route('/sw.js')
def service_worker():
  return send_from_directory('.', 'sw.js', mimetype='application/javascript')


# Home Page Builder
@app.route('/')
def index():
  return render_template('builder.html')


# AI Polish Route
@app.route('/enhance', methods=['POST'])
def enhance():
  data = request.get_json()
  raw_text = data.get('text', '')
  if not raw_text:
    return jsonify({'error': 'No input text'}), 400

  prompt = (
      f'Convert this rough student project/experience description into 3'
      f' clean, ATS-friendly bullet points with strong action verbs: {raw_text}'
  )
  try:
    response = client.models.generate_content(
        model='gemini-2.5-flash', contents=prompt
    )
    return jsonify({'enhanced': response.text})
  except Exception as e:
    return jsonify({'error': str(e)}), 500


# Save Portfolio
@app.route('/save', methods=['POST'])
def save_portfolio():
  name = request.form.get('name')
  slug = name.lower().strip().replace(' ', '-').replace('.', '')

  existing = FullPortfolio.query.filter_by(slug=slug).first()
  if existing:
    slug = f'{slug}-{FullPortfolio.query.count() + 1}'

  p_titles = request.form.getlist('project_title[]')
  p_techs = request.form.getlist('project_tech[]')
  p_links = request.form.getlist('project_link[]')
  p_descs = request.form.getlist('project_desc[]')

  projects = []
  for i in range(len(p_titles)):
    if p_titles[i].strip():
      projects.append({
          'title': p_titles[i],
          'tech': p_techs[i] if i < len(p_techs) else '',
          'link': p_links[i] if i < len(p_links) else '',
          'desc': p_descs[i] if i < len(p_descs) else '',
      })

  exp_roles = request.form.getlist('exp_role[]')
  exp_orgs = request.form.getlist('exp_org[]')
  exp_durations = request.form.getlist('exp_duration[]')
  exp_details = request.form.getlist('exp_detail[]')

  experiences = []
  for i in range(len(exp_roles)):
    if exp_roles[i].strip():
      experiences.append({
          'role': exp_roles[i],
          'org': exp_orgs[i] if i < len(exp_orgs) else '',
          'duration': exp_durations[i] if i < len(exp_durations) else '',
          'detail': exp_details[i] if i < len(exp_details) else '',
      })

  c_names = request.form.getlist('cert_name[]')
  c_issuers = request.form.getlist('cert_issuer[]')

  certificates = []
  for i in range(len(c_names)):
    if c_names[i].strip():
      certificates.append({
          'name': c_names[i],
          'issuer': c_issuers[i] if i < len(c_issuers) else '',
      })

  new_user = FullPortfolio(
      slug=slug,
      name=name,
      gender=request.form.get('gender', 'male'),
      tagline=request.form.get('tagline'),
      email=request.form.get('email'),
      github=request.form.get('github'),
      linkedin=request.form.get('linkedin'),
      bio=request.form.get('bio'),
      education_degree=request.form.get('education_degree'),
      education_college=request.form.get('education_college'),
      education_year=request.form.get('education_year'),
      skills_core=request.form.get('skills_core'),
      skills_tools=request.form.get('skills_tools'),
      projects_json=json.dumps(projects),
      experience_json=json.dumps(experiences),
      certificates_json=json.dumps(certificates),
      theme=request.form.get('theme', 'theme_modern'),
  )
  db.session.add(new_user)
  db.session.commit()
  return redirect(url_for('view_portfolio', slug=slug))


# View Portfolio
@app.route('/p/<slug>')
def view_portfolio(slug):
  user = FullPortfolio.query.filter_by(slug=slug).first_or_404()
  projects = json.loads(user.projects_json or '[]')
  experiences = json.loads(user.experience_json or '[]')
  certificates = json.loads(user.certificates_json or '[]')
  core_skills = [
      s.strip() for s in (user.skills_core or '').split(',') if s.strip()
  ]
  tools = [s.strip() for s in (user.skills_tools or '').split(',') if s.strip()]

  return render_template(
      f'{user.theme}.html',
      user=user,
      projects=projects,
      experiences=experiences,
      certificates=certificates,
      core_skills=core_skills,
      tools=tools,
  )


if __name__ == '__main__':
  app.run(host='0.0.0.0', port=5000, debug=True)