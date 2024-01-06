import discord
from .enums import *
from .utils import *

class AttackTeam(discord.ui.Select):
    def __init__(self, ctx: discord.ApplicationContext, teams: list, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.options = teams
        self.placeholder = "Quelle équipe voulez-vous attaquer ?"
        self.ctx = ctx
    
    async def callback(self, interaction: discord.Interaction):
        if self.ctx.author.id != interaction.user.id:
            await interaction.response.send_message("Vous n'avez pas le droit de faire ça.", ephemeral=True)
            return
        await interaction.response.send_message(f"Vous allez attaquer l'{interaction.guild.get_channel(int(self.values[0])).name} !", ephemeral=True)
        self.view.team = self.values[0]
        # On enlève le select sans supprimer les boutons qui sont déjà là
        self.view.remove_item(self)
        # On active les boutons
        self.view.children[0].disabled = False
        self.view.children[1].disabled = False
        await interaction.message.edit(view=self.view)
        

class Attack(discord.ui.View):
    def __init__(self, ctx: discord.ApplicationContext, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.ctx = ctx
        teams = list(Teams)
        self.team = None
        teams = [team.value for team in teams]
        options = [discord.SelectOption(label=self.ctx.guild.get_channel(team).name, value=str(team)) for team in teams if team != self.ctx.channel.parent_id]
        self.add_item(AttackTeam(self.ctx, options))


    @discord.ui.button(label="Furtivement", style=discord.ButtonStyle.green, custom_id="attack_stealth", disabled=True, row=1)
    async def attack_stealth(self, button: discord.ui.Button, interaction: discord.Interaction):
        if self.ctx.author.id != interaction.user.id:
            await interaction.response.send_message("Vous n'avez pas le droit de faire ça.", ephemeral=True)
            return
        await interaction.response.send_message(f"Vous attaquez furtivement l'{interaction.guild.get_channel(int(self.team)).name} !", ephemeral=True)
        webhook = await get_webhook(self.ctx.bot, self.ctx.channel.parent_id, "🔋")
        await webhook.send(f"J'attaque l'{interaction.guild.get_channel(int(self.team)).name.replace('-', ' ')} furtivement !", username=interaction.user.display_name, avatar_url=interaction.user.display_avatar.url, thread=self.ctx.channel)
        await interaction.message.delete()
        

    @discord.ui.button(label="Anonymement", style=discord.ButtonStyle.green, custom_id="attack_anonymously", disabled=True, row=1)
    async def attack_anonymously(self, button: discord.ui.Button, interaction: discord.Interaction):
        if self.ctx.author.id != interaction.user.id:
            await interaction.response.send_message("Vous n'avez pas le droit de faire ça.", ephemeral=True)
            return
        await interaction.response.send_message(f"Vous attaquez anonymement l'{interaction.guild.get_channel(int(self.team)).name} !", ephemeral=True)
        await interaction.message.edit(view=None)
        webhook = await get_webhook(self.ctx.bot, self.ctx.channel.parent_id, "🔋")
        await webhook.send(f"J'attaque l'{interaction.guild.get_channel(int(self.team)).name.replace('-', ' ')} furtivement !", username=interaction.user.display_name, avatar_url=interaction.user.display_avatar.url, thread=self.ctx.channel)
        await interaction.message.delete()

    


class Open(discord.ui.View):
    def __init__(self, ctx: discord.ApplicationContext, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.ctx = ctx

    @discord.ui.string_select(placeholder="Quel tier voulez-vous ouvrir ?", options=[discord.SelectOption(label="Caisse de tier 1", value="1"), discord.SelectOption(label="Caisse de tier 2", value="2")])
    async def open(self, select: discord.ui.Select, interaction: discord.Interaction):
        if self.ctx.author.id != interaction.user.id:
            await interaction.response.send_message("Vous n'avez pas le droit de faire ça.", ephemeral=True)
            return
        await interaction.response.send_message(f"Vous ouvrez une caisse de tier {select.values[0]} !", ephemeral=True)
        webhook = await get_webhook(self.ctx.bot, self.ctx.channel.parent_id, "🔋")
        await webhook.send(f"J'ouvre une caisse de tier {select.values[0]} ! *Vous recevrez le contenu de la caisse quand <@{Users.LUXIO.value}> sera disponible*", username=interaction.user.display_name, avatar_url=interaction.user.display_avatar.url, thread=self.ctx.channel)
        await interaction.message.delete()




class Use(discord.ui.Modal):
    def __init__(self, ctx: discord.ApplicationContext, error=False, object="", *args, **kwargs):
        super().__init__(title="Utiliser un objet/sort", *args, **kwargs)
        self.ctx = ctx
        self.teams = list(Teams)
        if error:
            self.add_item(discord.ui.InputText(label="Objet/sort à utiliser", style=discord.InputTextStyle.short, value=object))
            self.add_item(discord.ui.InputText(label="Équipe ciblée. Entrez un nombre valide", style=discord.InputTextStyle.singleline, max_length=1, required=False))
            print()
        else:
            self.add_item(discord.ui.InputText(label="Objet/sort à utiliser", style=discord.InputTextStyle.short))
            self.add_item(discord.ui.InputText(label="Équipe ciblée", style=discord.InputTextStyle.singleline, max_length=1, required=False))
        print(self.to_dict())


    async def callback(self, interaction: discord.Interaction):
        obj = self.children[0].value
        team = self.children[1].value
        # S'il n'y a pas de team, la team est celle de l'utilisateur
        if not team:
            team = self.ctx.channel.parent_id
        else:
            if team.isdigit() and int(team) <= len(self.teams):
                team = int(team)
                team = self.teams[team-1].value
            else:
                new_modal = Use(self.ctx, error=True, object=obj)
                await interaction.response.send_modal(new_modal)
                return
        webhook = await get_webhook(self.ctx.bot, self.ctx.channel.parent_id, "BTW")
        # Si c'est sur l'équipe de l'utilisateur
        if team == self.ctx.channel.parent_id:
            await webhook.send(f"J'utilise l'objet **{obj}**", username=interaction.user.display_name, avatar_url=interaction.user.display_avatar.url, thread=self.ctx.channel)
            await interaction.response.send_message(f"Vous utilisez l'objet **{obj}** !", ephemeral=True)
        else:
            await webhook.send(f"J'utilise l'objet **{obj}** sur l'{self.ctx.guild.get_channel(team).name.replace('-', '')} !", username=interaction.user.display_name, avatar_url=interaction.user.display_avatar.url, thread=self.ctx.channel)
            await interaction.response.send_message(f"Vous utilisez l'objet **{obj}** sur l'{self.ctx.guild.get_channel(team).name} !", ephemeral=True)




class Buy(discord.ui.View):
    def __init__(self, ctx: discord.ApplicationContext, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.ctx = ctx